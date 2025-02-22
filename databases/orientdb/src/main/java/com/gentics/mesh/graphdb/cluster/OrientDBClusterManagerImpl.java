package com.gentics.mesh.graphdb.cluster;

import static com.gentics.mesh.MeshEnv.CONFIG_FOLDERNAME;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringEscapeUtils;

import com.gentics.mesh.Mesh;
import com.gentics.mesh.MeshStatus;
import com.gentics.mesh.cli.BootstrapInitializer;
import com.gentics.mesh.core.rest.admin.cluster.ClusterInstanceInfo;
import com.gentics.mesh.core.rest.admin.cluster.ClusterStatusResponse;
import com.gentics.mesh.etc.config.ClusterOptions;
import com.gentics.mesh.etc.config.GraphStorageOptions;
import com.gentics.mesh.etc.config.OrientDBMeshOptions;
import com.gentics.mesh.graphdb.OrientDBDatabase;
import com.gentics.mesh.graphdb.spi.GraphStorage;
import com.gentics.mesh.util.DateUtils;
import com.gentics.mesh.util.PropertyUtil;
import com.hazelcast.core.HazelcastInstance;
import com.orientechnologies.orient.core.Orient;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.server.OServer;
import com.orientechnologies.orient.server.OServerMain;
import com.orientechnologies.orient.server.distributed.ODistributedServerManager;
import com.orientechnologies.orient.server.distributed.ODistributedServerManager.DB_STATUS;
import com.orientechnologies.orient.server.hazelcast.OHazelcastPlugin;
import com.orientechnologies.orient.server.plugin.OServerPluginManager;

import dagger.Lazy;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.reactivex.core.TimeoutStream;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

/**
 * Manager for OrientDB cluster and server features.
 *
 * The manager handles the OrientDB cluster/server configuration, OrientDB studio plugin installation, OrientDB server startup.
 *
 * Additionally the manager also provides methods to access the cluster information. The {@link TopologyEventBridge} is installed by the manager during the
 * startup to handle cluster specific events.
 */
@Singleton
public class OrientDBClusterManagerImpl implements OrientDBClusterManager {

	private static final Logger log = LoggerFactory.getLogger(OrientDBClusterManagerImpl.class);

	private static final String ORIENTDB_PLUGIN_FOLDERNAME = "orientdb-plugins";

	private static final String ORIENTDB_STUDIO_ZIP = "orientdb-studio-3.1.6.zip";

	private static final String ORIENTDB_DISTRIBUTED_CONFIG = "default-distributed-db-config.json";

	private static final String ORIENTDB_SERVER_CONFIG = "orientdb-server-config.xml";

	private static final String ORIENTDB_BACKUP_CONFIG = "automatic-backup.json";

	private static final String ORIENTDB_SECURITY_SERVER_CONFIG = "security.json";

	private static final String ORIENTDB_HAZELCAST_CONFIG = "hazelcast.xml";

	private static final String MESH_MEMBER_DISK_QUOTA_EXCEEDED = "mesh.instance.disk-quota-exceeded";

	private OServer server;

	private OHazelcastPlugin hazelcastPlugin;

	private TopologyEventBridge topologyEventBridge;

	private final Mesh mesh;

	private final Lazy<Vertx> vertx;

	private final OrientDBMeshOptions options;

	private final Lazy<OrientDBDatabase> db;

	private final Lazy<BootstrapInitializer> boot;

	private final ClusterOptions clusterOptions;

	private final boolean isClusteringEnabled;

	@Inject
	public OrientDBClusterManagerImpl(Mesh mesh, Lazy<Vertx> vertx, Lazy<BootstrapInitializer> boot, OrientDBMeshOptions options, Lazy<OrientDBDatabase> db) {
		this.mesh = mesh;
		this.vertx = vertx;
		this.boot = boot;
		this.options = options;
		this.db = db;
		this.clusterOptions = options.getClusterOptions();
		this.isClusteringEnabled = clusterOptions != null && clusterOptions.isEnabled();
	}

	/**
	 * Create the needed configuration files in the filesystem if they can't be located.
	 *
	 * @throws IOException
	 */
	@Override
	public void initConfigurationFiles() throws IOException {

		File distributedConfigFile = new File(CONFIG_FOLDERNAME + "/" + ORIENTDB_DISTRIBUTED_CONFIG);
		if (!distributedConfigFile.exists()) {
			log.info("Creating orientdb distributed server configuration file {" + distributedConfigFile + "}");
			writeDistributedConfig(distributedConfigFile);
		}

		File hazelcastConfigFile = new File(CONFIG_FOLDERNAME + "/" + ORIENTDB_HAZELCAST_CONFIG);
		if (!hazelcastConfigFile.exists()) {
			log.info("Creating orientdb hazelcast configuration file {" + hazelcastConfigFile + "}");
			writeHazelcastConfig(hazelcastConfigFile);
		}

		File serverConfigFile = new File(CONFIG_FOLDERNAME + "/" + ORIENTDB_SERVER_CONFIG);
		// Check whether the initial configuration needs to be written
		if (!serverConfigFile.exists()) {
			log.info("Creating orientdb server configuration file {" + serverConfigFile + "}");
			writeOrientServerConfig(serverConfigFile);
		}

		File backupConfigFile = new File(CONFIG_FOLDERNAME + "/" + ORIENTDB_BACKUP_CONFIG);
		// Check whether the initial configuration needs to be written
		if (!backupConfigFile.exists()) {
			log.info("Creating orientdb backup configuration file {" + backupConfigFile + "}");
			writeOrientBackupConfig(backupConfigFile);
		}

		File securityConfigFile = new File(CONFIG_FOLDERNAME + "/" + ORIENTDB_SECURITY_SERVER_CONFIG);
		// Check whether the initial configuration needs to be written
		if (!securityConfigFile.exists()) {
			log.info("Creating orientdb server security configuration file {" + securityConfigFile + "}");
			writeOrientServerSecurityConfig(securityConfigFile);
		}

	}

	private void writeOrientServerSecurityConfig(File securityConfigFile) throws IOException {
		String resourcePath = "/config/" + ORIENTDB_SECURITY_SERVER_CONFIG;
		InputStream configIns = getClass().getResourceAsStream(resourcePath);
		if (configIns == null) {
			log.error("Could not find default orientdb server security configuration file {" + resourcePath + "} within classpath.");
		}
		StringWriter writer = new StringWriter();
		IOUtils.copy(configIns, writer, StandardCharsets.UTF_8);
		String configString = writer.toString();
		FileUtils.writeStringToFile(securityConfigFile, configString);
	}

	private void writeHazelcastConfig(File hazelcastConfigFile) throws IOException {
		String resourcePath = "/config/" + ORIENTDB_HAZELCAST_CONFIG;
		InputStream configIns = getClass().getResourceAsStream(resourcePath);
		if (configIns == null) {
			log.error("Could not find default hazelcast configuration file {" + resourcePath + "} within classpath.");
		}
		StringWriter writer = new StringWriter();
		IOUtils.copy(configIns, writer, StandardCharsets.UTF_8);
		String configString = writer.toString();
		FileUtils.writeStringToFile(hazelcastConfigFile, configString);
	}

	private void writeDistributedConfig(File distributedConfigFile) throws IOException {
		String resourcePath = "/config/" + ORIENTDB_DISTRIBUTED_CONFIG;
		InputStream configIns = getClass().getResourceAsStream(resourcePath);
		if (configIns == null) {
			log.error("Could not find default distributed configuration file {" + resourcePath + "} within classpath.");
		}
		StringWriter writer = new StringWriter();
		IOUtils.copy(configIns, writer, StandardCharsets.UTF_8);
		String configString = writer.toString();
		FileUtils.writeStringToFile(distributedConfigFile, configString);
	}

	/**
	 * Determine the OrientDB Node name.
	 *
	 * @return
	 */
	public String getNodeName() {
		StringBuilder nameBuilder = new StringBuilder();
		String nodeName = options.getNodeName();
		nameBuilder.append(nodeName);

		// Sanitize the name
		String name = nameBuilder.toString();
		name = name.replaceAll(" ", "_");
		name = name.replaceAll("\\.", "-");
		return name;
	}

	private String escapeSafe(String text) {
		return StringEscapeUtils.escapeJava(StringEscapeUtils.escapeXml11(new File(text).getAbsolutePath()));
	}

	private String getOrientServerConfig() throws Exception {
		File configFile = new File(CONFIG_FOLDERNAME + "/" + ORIENTDB_SERVER_CONFIG);
		String configString = FileUtils.readFileToString(configFile);

		// Now replace the parameters within the configuration
		String pluginDir = Matcher.quoteReplacement(new File(ORIENTDB_PLUGIN_FOLDERNAME).getAbsolutePath());
		System.setProperty("ORIENTDB_PLUGIN_DIR", pluginDir);
		System.setProperty("plugin.directory", pluginDir);
		// configString = configString.replaceAll("%CONSOLE_LOG_LEVEL%", "info");
		// configString = configString.replaceAll("%FILE_LOG_LEVEL%", "info");
		System.setProperty("ORIENTDB_CONFDIR_NAME", CONFIG_FOLDERNAME);
		System.setProperty("ORIENTDB_NODE_NAME", getNodeName());
		System.setProperty("ORIENTDB_DISTRIBUTED", String.valueOf(options.getClusterOptions().isEnabled()));
		String networkHost = options.getClusterOptions().getNetworkHost();
		if (isEmpty(networkHost)) {
			networkHost = "0.0.0.0";
		}

		System.setProperty("ORIENTDB_NETWORK_HOST", networkHost);
		// Only use the cluster network host if clustering is enabled.
		String dbDir = storageOptions().getDirectory();
		if (dbDir != null) {
			System.setProperty("ORIENTDB_DB_PATH", escapeSafe(storageOptions().getDirectory()));
		} else {
			if (log.isDebugEnabled()) {
				log.debug("Not setting ORIENTDB_DB_PATH because no database dir was configured.");
			}
		}
		configString = PropertyUtil.resolve(configString);
		if (log.isDebugEnabled()) {
			log.debug("OrientDB server configuration:" + configString);
		}

		// Apply on-the-fly fix for changed OrientDB configuration.
		final String NEW_PLUGIN = "com.orientechnologies.orient.server.hazelcast.OHazelcastPlugin";
		final String OLD_PLUGIN_REGEX = "com\\.gentics\\.mesh\\.graphdb\\.cluster\\.MeshOHazelcastPlugin";
		configString = configString.replaceAll(OLD_PLUGIN_REGEX, NEW_PLUGIN);

		return configString;
	}

	@Override
	public HazelcastInstance getHazelcast() {
		return hazelcastPlugin != null ? hazelcastPlugin.getHazelcastInstance() : null;
	}

	@Override
	public ClusterManager getVertxClusterManager() {
		HazelcastInstance hazelcast = getHazelcast();
		Objects.requireNonNull(hazelcast, "The hazelcast instance was not yet initialized.");
		return new HazelcastClusterManager(hazelcast);
	}

	private void writeOrientBackupConfig(File configFile) throws IOException {
		String resourcePath = "/config/automatic-backup.json";
		InputStream configIns = getClass().getResourceAsStream(resourcePath);
		if (configFile == null) {
			throw new RuntimeException("Could not find default orientdb backup configuration template file {" + resourcePath + "} within classpath.");
		}
		StringWriter writer = new StringWriter();
		IOUtils.copy(configIns, writer, StandardCharsets.UTF_8);
		String configString = writer.toString();
		FileUtils.writeStringToFile(configFile, configString);
	}

	private void writeOrientServerConfig(File configFile) throws IOException {
		String resourcePath = "/config/" + ORIENTDB_SERVER_CONFIG;
		InputStream configIns = getClass().getResourceAsStream(resourcePath);
		if (configFile == null) {
			throw new RuntimeException("Could not find default orientdb server configuration template file {" + resourcePath + "} within classpath.");
		}
		StringWriter writer = new StringWriter();
		IOUtils.copy(configIns, writer, StandardCharsets.UTF_8);
		String configString = writer.toString();
		FileUtils.writeStringToFile(configFile, configString);
	}

	/**
	 * Check the orientdb plugin directory and extract the orientdb studio plugin if needed.
	 *
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private void updateOrientDBPlugin() throws FileNotFoundException, IOException {
		try (InputStream ins = getClass().getResourceAsStream("/plugins/" + ORIENTDB_STUDIO_ZIP)) {
			File pluginDirectory = new File(ORIENTDB_PLUGIN_FOLDERNAME);
			pluginDirectory.mkdirs();

			// Remove old plugins
			boolean currentPluginFound = false;
			for (File plugin : pluginDirectory.listFiles()) {
				if (plugin.isFile()) {
					String filename = plugin.getName();
					log.debug("Checking orientdb plugin: " + filename);
					if (filename.equals(ORIENTDB_STUDIO_ZIP)) {
						currentPluginFound = true;
						continue;
					}
					if (filename.startsWith("orientdb-studio-")) {
						if (!plugin.delete()) {
							log.error("Could not delete old plugin {" + plugin + "}");
						}
					}
				}
			}

			if (!currentPluginFound) {
				log.info("Extracting OrientDB Studio");
				IOUtils.copy(ins, new FileOutputStream(new File(pluginDirectory, ORIENTDB_STUDIO_ZIP)));
			}
		}
	}

	private void postStartupDBEventHandling() {
		// Get the database status
		DB_STATUS status = server.getDistributedManager().getDatabaseStatus(getNodeName(), "storage");
		// Pass it along to the topology event bridge
		topologyEventBridge.onDatabaseChangeStatus(getNodeName(), "storage", status);
	}

	/**
	 * Query the OrientDB API and load cluster information which will be added to a {@link ClusterStatusResponse} response.
	 *
	 * @return Cluster status REST response
	 */
	public ClusterStatusResponse getClusterStatus() {
		ClusterStatusResponse response = new ClusterStatusResponse();
		if (hazelcastPlugin != null) {
			ODocument distribCfg = hazelcastPlugin.getClusterConfiguration();
			ODocument dbConfig = (ODocument) hazelcastPlugin.getConfigurationMap().get("database.storage");
			ODocument serverConfig = dbConfig.field("servers");

			Collection<ODocument> members = distribCfg.field("members");
			if (members != null) {
				for (ODocument m : members) {
					if (m == null) {
						continue;
					}
					ClusterInstanceInfo instanceInfo = new ClusterInstanceInfo();
					String name = m.field("name");

					int idx = name.indexOf("@");
					if (idx > 0) {
						name = name.substring(0, idx);
					}

					instanceInfo.setName(name);
					instanceInfo.setStatus(m.field("status"));
					Date date = m.field("startedOn");
					instanceInfo.setStartDate(DateUtils.toISO8601(date.getTime()));

					String address = null;
					Collection<Map> listeners = m.field("listeners");
					if (listeners != null) {
						for (Map l : listeners) {
							String protocol = (String) l.get("protocol");
							if (protocol.equals("ONetworkProtocolBinary")) {
								address = (String) l.get("listen");
							}
						}
					}
					instanceInfo.setAddress(address);
					instanceInfo.setRole(serverConfig.field(name));

					response.getInstances().add(instanceInfo);
				}
			}
		}
		return response;
	}

	/**
	 * Start the OrientDB studio server by extracting the studio plugin zipfile.
	 *
	 * @throws Exception
	 */
	@Override
	public void startAndSync() throws Exception {

		String orientdbHome = new File("").getAbsolutePath();
		System.setProperty("ORIENTDB_HOME", orientdbHome);

		if (server == null) {
			server = OServerMain.create(false);
			updateOrientDBPlugin();
		}

		if (clusterOptions != null && clusterOptions.isEnabled()) {
			// This setting will be referenced by the hazelcast configuration
			System.setProperty("mesh.clusterName", clusterOptions.getClusterName() + "@" + db.get().getDatabaseRevision());
		}

		log.info("Starting OrientDB Server");
		server.startup(getOrientServerConfig());
		activateServer();
	}

	private void activateServer() throws Exception {
		OServerPluginManager manager = new OServerPluginManager();
		manager.config(server);
		server.activate();
		// The mesh shutdown hook manages OrientDB shutdown.
		// We need to manage this ourself since hazelcast is otherwise shutdown before closing vert.x
		// When we control the shutdown we can ensure a clean shutdown process.
		Orient.instance().removeShutdownHook();

		if (isClusteringEnabled) {
			ODistributedServerManager distributedManager = server.getDistributedManager();
			if (server.getDistributedManager() instanceof OHazelcastPlugin) {
				hazelcastPlugin = (OHazelcastPlugin) distributedManager;
			}

			topologyEventBridge = new TopologyEventBridge(options, vertx, boot, this, getHazelcast());
			distributedManager.registerLifecycleListener(topologyEventBridge);
		}

		manager.startup();

		if (isClusteringEnabled) {
			// The registerLifecycleListener may not have been invoked. We need to redirect the online event manually.
			postStartupDBEventHandling();
			if (!options.isInitClusterMode()) {
				mesh.setStatus(MeshStatus.WAITING_FOR_CLUSTER);
				joinCluster();
				mesh.setStatus(MeshStatus.STARTING);
				// Add a safety margin
				Thread.sleep(options.getClusterOptions().getTopologyLockDelay());
			}
		}

	}

	/**
	 * Join the cluster and block until the graph database has been received.
	 *
	 * @throws InterruptedException
	 */
	private void joinCluster() throws InterruptedException {
		// Wait until another node joined the cluster
		int timeout = options.getStorageOptions().getClusterJoinTimeout();
		log.info("Waiting {" + timeout + "} milliseconds for other nodes in the cluster.");
		if (!topologyEventBridge.waitForMainGraphDB(timeout, MILLISECONDS)) {
			throw new RuntimeException("Waiting for cluster database source timed out after {" + timeout + "} milliseconds.");
		}
	}

	@Override
	public void stop() {
		log.info("Stopping cluster manager");
		if (server != null) {
			log.info("Stopping OrientDB Server");
			server.shutdown();
		}
	}

	public boolean isServerActive() {
		return server != null && server.isActive();
	}

	public OServer getServer() {
		return server;
	}

	/**
	 * Return the graph database storage options.
	 *
	 * @return
	 */
	public GraphStorageOptions storageOptions() {
		return options.getStorageOptions();
	}

	// /**
	// * Removes the server/node from the distributed configuration.
	// *
	// * @param iNode
	// */
	// public void removeNode(String iNode) {
	// log.info(
	// "Removing server {" + iNode + "} from distributed configuration on server {" + getNodeName() + "} in cluster {" + getClusterName() + "}");
	// server.getDistributedManager().removeServer(iNode, true);
	// }

	public OHazelcastPlugin getHazelcastPlugin() {
		return hazelcastPlugin;
	}

	/**
	 * @see TopologyEventBridge#isClusterTopologyLocked()
	 * @return
	 */
	@Override
	public boolean isClusterTopologyLocked() {
		return isClusterTopologyLocked(true);
	}

	/**
	 * Checks if the cluster storage is locked cluster-wide.
	 * @param doLog true if log messages shall be created, false if not
	 * @return true iff cluster storage is locked
	 */
	protected boolean isClusterTopologyLocked(boolean doLog) {
		if (topologyEventBridge == null) {
			return false;
		} else {
			return topologyEventBridge.isClusterTopologyLocked(doLog);
		}
	}

	@Override
	public Completable waitUntilWriteQuorumReached() {
		return waitUntilTrue(() -> isWriteQuorumReached() && !isClusterTopologyLocked(false));
	}

	@Override
	public Completable waitUntilLocalNodeOnline() {
		return waitUntilTrue(this::isLocalNodeOnline);
	}

	@Override
	public boolean isLocalNodeOnline() {
		if (isClusteringEnabled) {
			if (server != null && server.getDistributedManager() != null) {
				ODistributedServerManager distributedManager = server.getDistributedManager();
				String localNodeName = distributedManager.getLocalNodeName();
				boolean online = distributedManager.isNodeOnline(localNodeName, GraphStorage.DB_NAME);
				if (log.isDebugEnabled()) {
					log.debug("State of DB {} in local node {} is {}", GraphStorage.DB_NAME, localNodeName, online);
				}
				return online;
			} else {
				log.error("Could not check DB state of local node {}");
				return false;
			}
		} else {
			return true;
		}
	}

	@Override
	public boolean isWriteQuorumReached() {
		if (!isClusteringEnabled) {
			return true;
		}

		try {
			// The server and manager may not yet be initialized. We need to wait until those are ready
			if (server != null && server.getDistributedManager() != null) {
				return server.getDistributedManager().isWriteQuorumPresent(GraphStorage.DB_NAME);
			} else {
				return false;
			}
		} catch (Throwable e) {
			log.error("Error while checking write quorum", e);
			return false;
		}
	}

	/**
	 * Check whether any of the cluster members is set to have exceeded the disk quota
	 * @return Optional uuid of the (first found) instance having the disk quota exceeded
	 */
	public Optional<String> getInstanceDiskQuotaExceeded() {
		if (!isClusteringEnabled || hazelcastPlugin == null) {
			return Optional.empty();
		} else {
			return hazelcastPlugin.getHazelcastInstance().getCluster().getMembers().stream()
					.filter(m -> m.getBooleanAttribute(MESH_MEMBER_DISK_QUOTA_EXCEEDED) == Boolean.TRUE)
					.map(m -> m.getUuid()).findFirst();
		}
	}

	/**
	 * Set the disk-quota-exceeded status of the local cluster member (if clustering enabled) 
	 * @param diskQuotaExceeded disk-quota-exceeded status
	 */
	public void setLocalMemberDiskQuotaExceeded(boolean diskQuotaExceeded) {
		if (hazelcastPlugin != null) {
			hazelcastPlugin.getHazelcastInstance().getCluster().getLocalMember()
					.setBooleanAttribute(MESH_MEMBER_DISK_QUOTA_EXCEEDED, diskQuotaExceeded);
		}
	}

	private Completable waitUntilTrue(BooleanSupplier predicate) {
		return Completable.defer(() -> {
			if (predicate.getAsBoolean()) {
				return Completable.complete();
			}
			return Observable.using(
				() -> new io.vertx.reactivex.core.Vertx(vertx.get()).periodicStream(1000),
				TimeoutStream::toObservable,
				TimeoutStream::cancel).takeUntil(ignore -> {
					return predicate.getAsBoolean();
				}).ignoreElements();
		});
	}
}
