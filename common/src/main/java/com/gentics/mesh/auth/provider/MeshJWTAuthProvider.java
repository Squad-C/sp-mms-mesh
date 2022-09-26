package com.gentics.mesh.auth.provider;

import static com.gentics.mesh.core.rest.error.Errors.error;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.gentics.mesh.auth.AuthenticationResult;
import com.gentics.mesh.cli.BootstrapInitializer;
import com.gentics.mesh.context.InternalActionContext;
import com.gentics.mesh.core.data.MeshAuthUser;
import com.gentics.mesh.core.rest.auth.TokenResponse;
import com.gentics.mesh.etc.config.AuthenticationOptions;
import com.gentics.mesh.etc.config.MeshOptions;
import com.gentics.mesh.graphdb.spi.Database;
import com.gentics.mesh.util.JWTUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.jwt.JWTOptions;
import io.vertx.ext.web.Cookie;

/**
 * Central mesh authentication provider which will handle JWT.
 */
@Singleton
public class MeshJWTAuthProvider implements AuthProvider, JWTAuth {

	private static final Logger log = LoggerFactory.getLogger(MeshJWTAuthProvider.class);

	public static final String TOKEN_COOKIE_KEY = "mesh.token";
	private static final String USERID_FIELD_NAME = "userUuid";
	private static final String API_KEY_TOKEN_CODE_FIELD_NAME = "jti";

	private final Vertx vertx;
	protected final Database db;
	private final BCryptPasswordEncoder passwordEncoder;
	private final BootstrapInitializer boot;
	private final MeshOptions meshOptions;

	private JWTAuth jwtProvider;

	@Inject
	public MeshJWTAuthProvider(Vertx vertx, MeshOptions meshOptions, BCryptPasswordEncoder passwordEncoder, Database database, BootstrapInitializer boot) {
		this.vertx = vertx;
		this.meshOptions = meshOptions;
		this.passwordEncoder = passwordEncoder;
		this.db = database;
		this.boot = boot;
	}

	public void initialize() {
		if (this.jwtProvider != null) {
			return;
		}

		// Use the mesh JWT options in order to setup the JWTAuth provider
		AuthenticationOptions options = meshOptions.getAuthenticationOptions();
		String keystorePassword = options.getKeystorePassword();
		if (keystorePassword == null) {
			throw new RuntimeException("The keystore password could not be found within the authentication options.");
		}

		String keyStorePath = options.getKeystorePath();
		String type = "jceks";
		JWTAuthOptions config = new JWTAuthOptions();
		// Set JWT options from the config
		config.setJWTOptions(JWTUtil.createJWTOptions(options));
		config.setKeyStore(new KeyStoreOptions().setPath(keyStorePath).setPassword(keystorePassword).setType(type));
		this.jwtProvider = JWTAuth.create(this.vertx, new JWTAuthOptions(config));
	}

	public void authenticateJWT(JsonObject authInfo, Handler<AsyncResult<AuthenticationResult>> resultHandler) {
		this.initialize();

		// Decode and validate the JWT. A JWTUser will be returned which contains the decoded token.
		// We will use this information to load the Mesh User from the graph.
		jwtProvider.authenticate(authInfo, rh -> {
			if (rh.failed()) {
				if (log.isDebugEnabled()) {
					log.debug("Could not authenticate token.", rh.cause());
				} else {
					log.warn("Could not authenticate token.");
				}
				resultHandler.handle(Future.failedFuture("Invalid Token"));
				return;
			}

			JsonObject decodedJwt = rh.result().principal();
			try {
				User user = loadUserByJWT(decodedJwt);
				AuthenticationResult result = new AuthenticationResult(user);

				// Check whether an api key was used to authenticate the user.
				if (decodedJwt.containsKey(API_KEY_TOKEN_CODE_FIELD_NAME)) {
					result.setUsingAPIKey(true);
				}
				resultHandler.handle(Future.succeededFuture(result));
			} catch (Exception e) {
				resultHandler.handle(Future.failedFuture(e));
			}
		});
	}

	@Override
	public void authenticate(JsonObject authInfo, Handler<AsyncResult<User>> resultHandler) {
		// The mesh auth provider is not using this method to authenticate a user.
		throw new NotImplementedException();
	}

	@Override
	public String generateToken(JsonObject claims, JWTOptions options) {
		throw new NotImplementedException();
	}

	/**
	 * Authenticates the user and returns a JWToken if successful.
	 *
	 * @param username
	 *            Username
	 * @param password
	 *            Password
	 * @param resultHandler
	 *            Handler to be invoked with the created JWToken
	 */
	public void generateToken(String username, String password, String newPassword, Handler<AsyncResult<String>> resultHandler) {
		this.initialize();

		authenticate(username, password, newPassword, rh -> {
			if (rh.failed()) {
				resultHandler.handle(Future.failedFuture(rh.cause()));
				return;
			}

			User user = rh.result().getUser();
			String uuid;
			if (user instanceof MeshAuthUser) {
				uuid = db.tx(((MeshAuthUser) user)::getUuid);
			} else {
				uuid = user.principal().getString("uuid");
			}
			JsonObject tokenData = new JsonObject().put(USERID_FIELD_NAME, uuid);
			JWTOptions jwt = JWTUtil.createJWTOptions(meshOptions.getAuthenticationOptions())
				.setExpiresInSeconds(meshOptions.getAuthenticationOptions().getTokenExpirationTime());
			resultHandler.handle(Future.succeededFuture(jwtProvider.generateToken(tokenData, jwt)));
		});
	}

	/**
	 * Load the user with the given username and use the bcrypt encoder to compare the user password with the provided password.
	 *
	 * @param username
	 *            Username
	 * @param password
	 *            Password
	 * @param resultHandler
	 *            Handler which will be invoked which will return the authenticated user or fail if the credentials do not match or the user could not be found
	 */
	private void authenticate(String username, String password, String newPassword, Handler<AsyncResult<AuthenticationResult>> resultHandler) {
		MeshAuthUser user = db.tx(() -> boot.userRoot().findMeshAuthUserByUsername(username));
		if (user == null) {
			if (log.isDebugEnabled()) {
				log.debug("Could not load user with username {" + username + "}.");
			}
			// TODO: Don't let the user know that we know that he did not exist?
			resultHandler.handle(Future.failedFuture(error(UNAUTHORIZED, "auth_login_failed")));
			return;
		}

		String accountPasswordHash = db.tx(user::getPasswordHash);
		// TODO: check if user is enabled
		if (StringUtils.isEmpty(accountPasswordHash) && password != null) {
			if (log.isDebugEnabled()) {
				log.debug("The account password hash or token password string are invalid.");
			}
			resultHandler.handle(Future.failedFuture(error(UNAUTHORIZED, "auth_login_failed")));
			return;
		}

		if (log.isDebugEnabled()) {
			log.debug("Validating password using the bcrypt password encoder");
		}

		if (!passwordEncoder.matches(password, accountPasswordHash)) {
			resultHandler.handle(Future.failedFuture(error(UNAUTHORIZED, "auth_login_failed")));
			return;
		}

		boolean forcedPasswordChange = db.tx(user::isForcedPasswordChange);
		if (forcedPasswordChange && newPassword == null) {
			resultHandler.handle(Future.failedFuture(error(BAD_REQUEST, "auth_login_password_change_required")));
			return;
		} else if (!forcedPasswordChange && newPassword != null) {
			resultHandler.handle(Future.failedFuture(error(BAD_REQUEST, "auth_login_newpassword_failed")));
			return;
		}

		if (forcedPasswordChange) {
			db.tx(() -> user.setPassword(newPassword));
		}

		resultHandler.handle(Future.succeededFuture(new AuthenticationResult(user)));
	}

	/**
	 * Generates a new JWToken with the user.
	 *
	 * @param user
	 *            User
	 * @return The new token
	 */
	public String generateToken(User user) {
		this.initialize();
		if (!(user instanceof MeshAuthUser)) {
			log.error("Can't generate token for user of type {" + user.getClass().getName() + "}");
			throw error(INTERNAL_SERVER_ERROR, "error_internal");
		}

		AuthenticationOptions options = meshOptions.getAuthenticationOptions();
		JsonObject tokenData = new JsonObject();
		String uuid = db.tx(((MeshAuthUser) user)::getUuid);
		tokenData.put(USERID_FIELD_NAME, uuid);
		JWTOptions jwtOptions = JWTUtil.createJWTOptions(options);
		return jwtProvider.generateToken(tokenData, jwtOptions);
	}

	/**
	 * Generate a new JWT which can be used as an API key.
	 *
	 * @param user
	 * @param tokenCode
	 *            Code which will be part of the JWT. This code is used to verify that the JWT is still valid
	 * @param expireDuration
	 * @return Generated API key
	 */
	public String generateAPIToken(com.gentics.mesh.core.data.User user, String tokenCode, Integer expireDuration) {
		this.initialize();
		AuthenticationOptions options = meshOptions.getAuthenticationOptions();
		JsonObject tokenData = new JsonObject()
			.put(USERID_FIELD_NAME, user.getUuid())
			.put(API_KEY_TOKEN_CODE_FIELD_NAME, tokenCode);
		JWTOptions jwtOptions = JWTUtil.createJWTOptions(options);
		if (expireDuration != null) {
			jwtOptions.setExpiresInMinutes(expireDuration);
		}
		return jwtProvider.generateToken(tokenData, jwtOptions);
	}

	/**
	 * Gets the corresponding {@link MeshAuthUser} by the Vert.x User.
	 *
	 * @param jwt
	 *            Decoded JWT
	 * @return Mesh user
	 * @throws Exception
	 */
	private User loadUserByJWT(JsonObject jwt) throws Exception {
		return db.tx(tx -> {
			String userUuid = jwt.getString(USERID_FIELD_NAME);
			MeshAuthUser user = boot.userRoot().findMeshAuthUserByUuid(userUuid);
			if (user == null) {
				if (log.isDebugEnabled()) {
					log.debug("Could not load user with UUID {" + userUuid + "}.");
				}
				// TODO use NoStackTraceThrowable?
				throw new Exception("Invalid credentials!");
			}
			// Set the uuid to cache it in the element. We know it is valid.
			user.setCachedUuid(userUuid);

			// TODO Re-enable isEnabled cache and check if User#delete behaviour changes
			//	if (!user.isEnabled()) {
			//		throw new Exception("User is disabled");
			//	}

			// Check whether the token might be an API key token
			if (!jwt.containsKey(JWTUtil.JWT_FIELD_EXPIRATION)) {
				String apiKeyToken = jwt.getString(API_KEY_TOKEN_CODE_FIELD_NAME);
				// TODO: All tokens without exp must have a token code - See https://github.com/gentics/mesh/issues/412
				if (apiKeyToken != null) {
					String storedApiKey = user.getAPIKeyTokenCode();
					// Verify that the API token is invalid.
					if (apiKeyToken != null && !apiKeyToken.equals(storedApiKey)) {
						throw new Exception("API key token is invalid.");
					}
				}
			}

			return user;
		});
	}

	/**
	 * Handle the login action and set a token cookie if the credentials are valid.
	 *
	 * @param ac
	 *            Action context used to add token cookie
	 * @param username
	 *            Username
	 * @param password
	 *            Password
	 */
	public void login(InternalActionContext ac, String username, String password, String newPassword) {
		generateToken(username, password, newPassword, rh -> {
			if (rh.failed()) {
				throw (RuntimeException) rh.cause();
			} else {
				ac.addCookie(Cookie.cookie(MeshJWTAuthProvider.TOKEN_COOKIE_KEY, rh.result())
					.setMaxAge(meshOptions.getAuthenticationOptions().getTokenExpirationTime()).setPath("/"));
				ac.send(new TokenResponse(rh.result()).toJson());
			}
		});
	}

}
