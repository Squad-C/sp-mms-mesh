package com.gentics.mesh.core.data.schema.impl;

import static com.gentics.mesh.core.data.relationship.GraphRelationships.HAS_SCHEMA_CONTAINER;
import static com.gentics.mesh.core.data.relationship.GraphRelationships.HAS_VERSION;
import static com.gentics.mesh.core.data.search.SearchQueueEntryAction.DELETE_ACTION;
import static com.gentics.mesh.core.data.search.SearchQueueEntryAction.UPDATE_ACTION;
import static com.gentics.mesh.core.data.service.ServerSchemaStorage.getSchemaStorage;
import static com.gentics.mesh.core.rest.error.Errors.conflict;
import static com.gentics.mesh.core.rest.error.Errors.error;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;

import java.io.IOException;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.gentics.mesh.cli.BootstrapInitializer;
import com.gentics.mesh.core.data.generic.AbstractMeshCoreVertex;
import com.gentics.mesh.core.data.node.Node;
import com.gentics.mesh.core.data.node.impl.NodeImpl;
import com.gentics.mesh.core.data.root.SchemaContainerRoot;
import com.gentics.mesh.core.data.schema.SchemaChange;
import com.gentics.mesh.core.data.schema.SchemaContainer;
import com.gentics.mesh.core.data.schema.handler.SchemaComparator;
import com.gentics.mesh.core.data.search.SearchQueueBatch;
import com.gentics.mesh.core.data.search.SearchQueueEntryAction;
import com.gentics.mesh.core.rest.schema.Schema;
import com.gentics.mesh.core.rest.schema.SchemaReference;
import com.gentics.mesh.core.rest.schema.SchemaResponse;
import com.gentics.mesh.core.rest.schema.SchemaUpdateRequest;
import com.gentics.mesh.core.rest.schema.change.impl.SchemaChangesListModel;
import com.gentics.mesh.core.rest.schema.impl.SchemaImpl;
import com.gentics.mesh.etc.MeshSpringConfiguration;
import com.gentics.mesh.graphdb.spi.Database;
import com.gentics.mesh.handler.InternalActionContext;
import com.gentics.mesh.json.JsonUtil;
import com.gentics.mesh.search.index.NodeIndexHandler;
import com.gentics.mesh.util.RestModelHelper;

import rx.Observable;

/**
 * @see SchemaContainer
 */
public class SchemaContainerImpl extends AbstractMeshCoreVertex<SchemaResponse, SchemaContainer> implements SchemaContainer {

	private static final String VERSION_PROPERTY_KEY = "version";

	public static void checkIndices(Database database) {
		database.addVertexType(SchemaContainerImpl.class);
	}

	@Override
	public SchemaReference createEmptyReferenceModel() {
		return new SchemaReference();
	}

	@Override
	public String getType() {
		return SchemaContainer.TYPE;
	}

	@Override
	public Observable<SchemaResponse> transformToRest(InternalActionContext ac, String... languageTags) {
		try {
			// Load the schema and add/overwrite some properties 
			SchemaResponse restSchema = JsonUtil.readSchema(getJson(), SchemaResponse.class);
			restSchema.setUuid(getUuid());

			// TODO Get list of projects to which the schema was assigned
			// for (Project project : getProjects()) {
			// }
			// ProjectResponse restProject = new ProjectResponse();
			// restProje ct.setUuid(project.getUuid());
			// restProject.setName(project.getName());
			// schemaResponse.getProjects().add(restProject);
			// }

			// Sort the list by project name
			// restSchema.getProjects()
			// Collections.sort(restSchema.getProjects(), new Comparator<ProjectResponse>() {
			// @Override
			// public int compare(ProjectResponse o1, ProjectResponse o2) {
			// return o1.getName().compareTo(o2.getName());
			// };
			// });

			// Role permissions
			RestModelHelper.setRolePermissions(ac, this, restSchema);

			restSchema.setPermissions(ac.getUser().getPermissionNames(ac, this));

			return Observable.just(restSchema);
		} catch (IOException e) {
			return Observable.error(e);
		}
	}

	@Override
	public List<? extends Node> getNodes() {
		return in(HAS_SCHEMA_CONTAINER).has(NodeImpl.class).toListExplicit(NodeImpl.class);
	}

	@Override
	public void delete() {
		// TODO should all references be updated to a new fallback schema?
		addIndexBatch(DELETE_ACTION);
		getElement().remove();
	}

	@Override
	public SchemaContainerImpl getImpl() {
		return this;
	}

	private String getJson() {
		return getProperty("json");
	}

	private void setJson(String json) {
		setProperty("json", json);
	}

	@Override
	public Schema getSchema() {
		Schema schema = getSchemaStorage().getSchema(getName(), getVersion());
		if (schema == null) {
			try {
				schema = JsonUtil.readSchema(getJson(), SchemaImpl.class);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			getSchemaStorage().addSchema(schema);
		}
		return schema;

	}

	@Override
	public void setSchema(Schema schema) {
		getSchemaStorage().removeSchema(schema.getName(), schema.getVersion());
		getSchemaStorage().addSchema(schema);
		String json = JsonUtil.toJson(schema);
		setJson(json);
		setProperty(VERSION_PROPERTY_KEY, schema.getVersion());
	}

	@Override
	public void setName(String name) {
		setProperty("name", name);
	}

	@Override
	public String getName() {
		return getProperty("name");
	}

	@Override
	public int getVersion() {
		return getProperty(VERSION_PROPERTY_KEY);
	}

	@Override
	public Observable<? extends SchemaContainer> update(InternalActionContext ac) {
		Database db = MeshSpringConfiguration.getInstance().database();
		SchemaContainerRoot root = BootstrapInitializer.getBoot().meshRoot().getSchemaContainerRoot();

		try {
			SchemaUpdateRequest requestModel = JsonUtil.readSchema(ac.getBodyAsString(), SchemaUpdateRequest.class);
			if (StringUtils.isEmpty(requestModel.getName())) {
				throw error(BAD_REQUEST, "error_name_must_be_set");
			}

			String schemaName = requestModel.getName();
			SchemaContainer foundSchema = root.findByName(schemaName).toBlocking().single();
			if (foundSchema != null && !foundSchema.getUuid().equals(getUuid())) {
				throw conflict(foundSchema.getUuid(), schemaName, "schema_conflicting_name", schemaName);
			}

			return db.trx(() -> {
				if (!getName().equals(requestModel.getName())) {
					setName(requestModel.getName());
				}
				// increase the version
				requestModel.setVersion(getVersion() + 1);
				setSchema(requestModel);
				return addIndexBatch(UPDATE_ACTION);
			}).process().map(i -> this);
		} catch (Exception e) {
			return Observable.error(e);
		}

	}

	@Override
	public Observable<SchemaChangesListModel> diff(InternalActionContext ac, SchemaComparator comparator) {
		try {
			SchemaChangesListModel list = new SchemaChangesListModel();
			SchemaUpdateRequest requestModel = JsonUtil.readSchema(ac.getBodyAsString(), SchemaUpdateRequest.class);
			requestModel.validate();
			list.getChanges().addAll(comparator.diff(transformToRest(ac, null).toBlocking().single(), requestModel));
			return Observable.just(list);
		} catch (Exception e) {
			return Observable.error(e);
		}

	}

	@Override
	public void addRelatedEntries(SearchQueueBatch batch, SearchQueueEntryAction action) {
		if (action == DELETE_ACTION) {
			// TODO Delete handling is not yet supported for schemas
		} else {
			String previousDocumentType = null;

			// TODO uncomment this code (in replacement for the following lines), as soon as getting previous schema containers is implemented
			//			SchemaContainer previousSchemaContainer = getPreviousVersion();
			//			if (previousSchemaContainer != null) {
			//				previousDocumentType = NodeIndexHandler.getDocumentType(previousSchemaContainer.getSchema());
			//			}
			int previousVersion = getVersion() - 1;
			if (previousVersion > 0) {
				previousDocumentType = getName() + "-" + previousVersion;
			}

			for (Node node : getNodes()) {
				batch.addEntry(node, UPDATE_ACTION);

				if (previousDocumentType != null) {
					List<String> languageNames = node.getAvailableLanguageNames();
					for (String languageTag : languageNames) {
						batch.addEntry(NodeIndexHandler.composeDocumentId(node, languageTag), node.getType(), DELETE_ACTION, previousDocumentType);
					}
				}
			}
		}
	}

	@Override
	public SchemaContainer getNextVersion() {
		return out(HAS_VERSION).has(SchemaContainerImpl.class).nextOrDefaultExplicit(SchemaContainerImpl.class, null);
	}

	@Override
	public SchemaContainer setNextVersion(SchemaContainer container) {
		setSingleLinkOutTo(container.getImpl(), HAS_VERSION);
		return this;
	}

	@Override
	public SchemaContainer getPreviousVersion() {
		return in(HAS_VERSION).has(SchemaContainerImpl.class).nextOrDefaultExplicit(SchemaContainerImpl.class, null);
	}

	@Override
	public SchemaContainer setPreviousVersion(SchemaContainer container) {
		setSingleLinkInTo(container.getImpl(), HAS_VERSION);
		return this;
	}

	@Override
	public SchemaChange getNextChange() {
		return (SchemaChange) out(HAS_SCHEMA_CONTAINER).nextOrDefault(null);
	}

	@Override
	public SchemaContainer setNextChange(SchemaChange change) {
		setSingleLinkOutTo(change.getImpl(), HAS_SCHEMA_CONTAINER);
		return this;
	}

	@Override
	public SchemaChange getPreviousChange() {
		return (SchemaChange) in(HAS_SCHEMA_CONTAINER).nextOrDefault(null);
	}

	@Override
	public SchemaContainer setPreviousChange(SchemaChange change) {
		setSingleLinkInTo(change.getImpl(), HAS_SCHEMA_CONTAINER);
		return this;
	}

}
