package com.gentics.mesh.core.endpoint.branch;

import static com.gentics.mesh.http.HttpConstants.APPLICATION_JSON;
import static io.netty.handler.codec.http.HttpResponseStatus.CREATED;
import static io.netty.handler.codec.http.HttpResponseStatus.NO_CONTENT;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.vertx.core.http.HttpMethod.DELETE;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.POST;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;

import com.gentics.mesh.auth.MeshAuthChain;
import com.gentics.mesh.cli.BootstrapInitializer;
import com.gentics.mesh.context.InternalActionContext;
import com.gentics.mesh.parameter.impl.GenericParametersImpl;
import com.gentics.mesh.parameter.impl.PagingParametersImpl;
import com.gentics.mesh.rest.InternalEndpointRoute;
import com.gentics.mesh.router.route.AbstractProjectEndpoint;
import com.gentics.mesh.util.UUIDUtil;

/**
 * Verticle for REST endpoints to manage branches.
 */
public class BranchEndpoint extends AbstractProjectEndpoint {

	private BranchCrudHandler crudHandler;

	public BranchEndpoint() {
		super("branches", null, null);
	}

	@Inject
	public BranchEndpoint(MeshAuthChain chain, BootstrapInitializer boot, BranchCrudHandler crudHandler) {
		super("branches", chain, boot);
		this.crudHandler = crudHandler;
	}

	@Override
	public String getDescription() {
		return "Provides endpoints which allow the manipulation of branches.";
	}

	@Override
	public void registerEndPoints() {
		secureAll();

		addCreateHandler();
		addSchemaInfoHandler();
		addMicroschemaInfoHandler();
		addReadHandler();
		addUpdateHandler();
		addNodeMigrationHandler();
		addMicronodeMigrationHandler();
		addTagsHandler();
	}

	private void addMicroschemaInfoHandler() {
		InternalEndpointRoute readMicroschemas = createRoute();
		readMicroschemas.path("/:branchUuid/microschemas");
		readMicroschemas.addUriParameter("branchUuid", "Uuid of the branch", UUIDUtil.randomUUID());
		readMicroschemas.method(GET);
		readMicroschemas.description("Load microschemas that are assigned to the branch and return a paged list response.");
		readMicroschemas.produces(APPLICATION_JSON);
		readMicroschemas.exampleResponse(OK, microschemaExamples.createMicroschemaReferenceList(), "List of microschemas.");
		readMicroschemas.addQueryParameters(PagingParametersImpl.class);
		readMicroschemas.handler(rc -> {
			InternalActionContext ac = wrap(rc);
			String uuid = rc.request().getParam("branchUuid");
			crudHandler.handleGetMicroschemaVersions(ac, uuid);
		});

		
	}

	private void addSchemaInfoHandler() {
		InternalEndpointRoute readSchemas = createRoute();
		readSchemas.path("/:branchUuid/schemas");
		readSchemas.addUriParameter("branchUuid", "Uuid of the branch", UUIDUtil.randomUUID());
		readSchemas.method(GET);
		readSchemas.description("Load schemas that are assigned to the branch and return a paged list response.");
		readSchemas.addQueryParameters(PagingParametersImpl.class);
		readSchemas.produces(APPLICATION_JSON);
		readSchemas.exampleResponse(OK, branchExamples.createSchemaReferenceList(), "Loaded schema list.");
		readSchemas.handler(rc -> {
			String uuid = rc.request().getParam("branchUuid");
			InternalActionContext ac = wrap(rc);
			crudHandler.handleGetSchemaVersions(ac, uuid);
		});
	}

	private void addNodeMigrationHandler() {
		InternalEndpointRoute endpoint = createRoute();
		endpoint.path("/:branchUuid/migrateSchemas");
		endpoint.method(POST);
		endpoint.addUriParameter("branchUuid", "Uuid of the branch", UUIDUtil.randomUUID());
		endpoint.description("Invoked the node migration for not yet migrated nodes of schemas that are assigned to the branch.");
		endpoint.exampleResponse(OK, miscExamples.createMessageResponse(), "schema_migration_invoked");
		endpoint.produces(APPLICATION_JSON);
		endpoint.handler(rc -> {
			InternalActionContext ac = wrap(rc);
			String branchUuid = rc.request().getParam("branchUuid");
			crudHandler.handleMigrateRemainingNodes(ac, branchUuid);
		});
	}

	private void addMicronodeMigrationHandler() {
		InternalEndpointRoute endpoint = createRoute();
		endpoint.path("/:branchUuid/migrateMicroschemas");
		endpoint.method(POST);
		endpoint.addUriParameter("branchUuid", "Uuid of the branch", UUIDUtil.randomUUID());
		endpoint.description("Invoked the micronode migration for not yet migrated micronodes of microschemas that are assigned to the branch.");
		endpoint.exampleResponse(OK, miscExamples.createMessageResponse(), "schema_migration_invoked");
		endpoint.produces(APPLICATION_JSON);
		endpoint.handler(rc -> {
			InternalActionContext ac = wrap(rc);
			String branchUuid = rc.request().getParam("branchUuid");
			crudHandler.handleMigrateRemainingMicronodes(ac, branchUuid);
		});
	}

	private void addCreateHandler() {
		InternalEndpointRoute endpoint = createRoute();
		endpoint.path("/");
		endpoint.method(POST);
		endpoint.description("Create a new branch and automatically invoke a node migration.");
		endpoint.produces(APPLICATION_JSON);
		endpoint.exampleRequest(versioningExamples.createBranchCreateRequest("Winter 2016"));
		endpoint.exampleResponse(CREATED, versioningExamples.createBranchResponse("Winter 2016"), "Created branch.");
		endpoint.handler(rc -> {
			InternalActionContext ac = wrap(rc);
			crudHandler.handleCreate(ac);
		});
	}

	private void addReadHandler() {
		InternalEndpointRoute readOne = createRoute();
		readOne.path("/:branchUuid");
		readOne.addUriParameter("branchUuid", "Uuid of the branch", UUIDUtil.randomUUID());
		readOne.description("Load the branch with the given uuid.");
		readOne.method(GET);
		readOne.produces(APPLICATION_JSON);
		readOne.exampleResponse(OK, versioningExamples.createBranchResponse("Summer Collection Branch"), "Loaded branch.");
		readOne.addQueryParameters(GenericParametersImpl.class);
		readOne.handler(rc -> {
			String uuid = rc.request().params().get("branchUuid");
			if (StringUtils.isEmpty(uuid)) {
				rc.next();
			} else {
				InternalActionContext ac = wrap(rc);
				crudHandler.handleRead(ac, uuid);
			}
		});

		InternalEndpointRoute readAll = createRoute();
		readAll.path("/");
		readAll.method(GET);
		readAll.description("Load multiple branches and return a paged list response.");
		readAll.exampleResponse(OK, versioningExamples.createBranchListResponse(), "Loaded branches.");
		readAll.addQueryParameters(PagingParametersImpl.class);
		readAll.addQueryParameters(GenericParametersImpl.class);
		readAll.produces(APPLICATION_JSON);
		readAll.handler(rc -> {
			InternalActionContext ac = wrap(rc);
			crudHandler.handleReadList(ac);
		});
	}

	private void addUpdateHandler() {
		InternalEndpointRoute addSchema = createRoute();
		addSchema.path("/:branchUuid/schemas");
		addSchema.addUriParameter("branchUuid", "Uuid of the branch", UUIDUtil.randomUUID());
		addSchema.method(POST);
		addSchema.description("Assign a schema version to the breanch.");
		addSchema.consumes(APPLICATION_JSON);
		addSchema.produces(APPLICATION_JSON);
		addSchema.exampleRequest(branchExamples.createSchemaReferenceList());
		addSchema.exampleResponse(OK, branchExamples.createSchemaReferenceList(), "Updated schema list.");
		addSchema.handler(rc -> {
			String uuid = rc.request().params().get("branchUuid");
			InternalActionContext ac = wrap(rc);
			crudHandler.handleAssignSchemaVersion(ac, uuid);
		});

		InternalEndpointRoute addMicroschema = createRoute();
		addMicroschema.path("/:branchUuid/microschemas");
		addMicroschema.addUriParameter("branchUuid", "Uuid of the branch", UUIDUtil.randomUUID());
		addMicroschema.method(POST);
		addMicroschema.description("Assign a microschema version to the branch.");
		addMicroschema.consumes(APPLICATION_JSON);
		addMicroschema.produces(APPLICATION_JSON);
		addMicroschema.exampleRequest(microschemaExamples.createMicroschemaReferenceList());
		addMicroschema.exampleResponse(OK, microschemaExamples.createMicroschemaReferenceList(), "Updated microschema list.");
		addMicroschema.handler(rc -> {
			InternalActionContext ac = wrap(rc);
			String uuid = rc.request().params().get("branchUuid");
			crudHandler.handleAssignMicroschemaVersion(ac, uuid);
		});

		InternalEndpointRoute setLatest = createRoute();
		setLatest.path("/:branchUuid/latest");
		setLatest.addUriParameter("branchUuid", "Uuid of the branch", UUIDUtil.randomUUID());
		setLatest.description("Set the branch with the given uuid as 'latest' branch of the project.");
		setLatest.method(POST);
		setLatest.produces(APPLICATION_JSON);
		setLatest.exampleResponse(OK, versioningExamples.createBranchResponse("Winter Collection Branch"), "Latest branch");
		setLatest.handler(rc -> {
			InternalActionContext ac = wrap(rc);
			String uuid = rc.request().params().get("branchUuid");
			crudHandler.handleSetLatest(ac, uuid);
		});

		InternalEndpointRoute updateBranch = createRoute();
		updateBranch.path("/:branchUuid");
		updateBranch.addUriParameter("branchUuid", "Uuid of the branch", UUIDUtil.randomUUID());
		updateBranch
				.description("Update the branch with the given uuid. The branch is created if no branch with the specified uuid could be found.");
		updateBranch.method(POST);
		updateBranch.consumes(APPLICATION_JSON);
		updateBranch.produces(APPLICATION_JSON);
		updateBranch.exampleRequest(versioningExamples.createBranchUpdateRequest("Winter Collection Branch"));
		updateBranch.exampleResponse(OK, versioningExamples.createBranchResponse("Winter Collection Branch"), "Updated branch");
		updateBranch.handler(rc -> {
			InternalActionContext ac = wrap(rc);
			String uuid = rc.request().params().get("branchUuid");
			crudHandler.handleUpdate(ac, uuid);
		});
	}

	private void addTagsHandler() {
		InternalEndpointRoute getTags = createRoute();
		getTags.path("/:branchUuid/tags");
		getTags.addUriParameter("branchUuid", "Uuid of the branch.", UUIDUtil.randomUUID());
		getTags.method(GET);
		getTags.produces(APPLICATION_JSON);
		getTags.exampleResponse(OK, tagExamples.createTagListResponse(), "List of tags that were used to tag the branch.");
		getTags.description("Return a list of all tags which tag the branch.");
		getTags.handler(rc -> {
			InternalActionContext ac = wrap(rc);
			String branchUuid = ac.getParameter("branchUuid");
			crudHandler.readTags(ac, branchUuid);
		});

		InternalEndpointRoute bulkUpdate = createRoute();
		bulkUpdate.path("/:branchUuid/tags");
		bulkUpdate.addUriParameter("branchUuid", "Uuid of the branch.", UUIDUtil.randomUUID());
		bulkUpdate.method(POST);
		bulkUpdate.produces(APPLICATION_JSON);
		bulkUpdate.description("Update the list of assigned tags");
		bulkUpdate.exampleRequest(tagExamples.getTagListUpdateRequest());
		bulkUpdate.exampleResponse(OK, tagExamples.createTagListResponse(), "Updated tag list.");
		bulkUpdate.handler(rc -> {
			InternalActionContext ac = wrap(rc);
			String branchUuid = ac.getParameter("branchUuid");
			crudHandler.handleBulkTagUpdate(ac, branchUuid);
		});

		InternalEndpointRoute addTag = createRoute();
		addTag.path("/:branchUuid/tags/:tagUuid");
		addTag.addUriParameter("branchUuid", "Uuid of the branch", UUIDUtil.randomUUID());
		addTag.addUriParameter("tagUuid", "Uuid of the tag", UUIDUtil.randomUUID());
		addTag.method(POST);
		addTag.produces(APPLICATION_JSON);
		addTag.exampleResponse(OK, versioningExamples.createBranchResponse("Summer Collection Branch"), "Updated branch.");
		addTag.description("Assign the given tag to the branch.");
		addTag.handler(rc -> {
			InternalActionContext ac = wrap(rc);
			String branchUuid = ac.getParameter("branchUuid");
			String tagUuid = ac.getParameter("tagUuid");
			crudHandler.handleAddTag(ac, branchUuid, tagUuid);
		});

		// TODO fix error handling. This does not fail when tagUuid could not be found
		InternalEndpointRoute removeTag = createRoute();
		removeTag.path("/:branchUuid/tags/:tagUuid");
		removeTag.addUriParameter("branchUuid", "Uuid of the branch", UUIDUtil.randomUUID());
		removeTag.addUriParameter("tagUuid", "Uuid of the tag", UUIDUtil.randomUUID());
		removeTag.method(DELETE);
		removeTag.produces(APPLICATION_JSON);
		removeTag.description("Remove the given tag from the branch.");
		removeTag.exampleResponse(NO_CONTENT, "Removal was successful.");
		removeTag.handler(rc -> {
			InternalActionContext ac = wrap(rc);
			String branchUuid = ac.getParameter("branchUuid");
			String tagUuid = ac.getParameter("tagUuid");
			crudHandler.handleRemoveTag(ac, branchUuid, tagUuid);
		});

	}
}
