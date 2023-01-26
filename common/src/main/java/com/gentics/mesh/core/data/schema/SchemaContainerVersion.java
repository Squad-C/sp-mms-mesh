package com.gentics.mesh.core.data.schema;

import static com.gentics.mesh.ElementType.SCHEMAVERSION;
import static com.gentics.mesh.core.rest.MeshEvent.SCHEMA_CREATED;
import static com.gentics.mesh.core.rest.MeshEvent.SCHEMA_DELETED;
import static com.gentics.mesh.core.rest.MeshEvent.SCHEMA_UPDATED;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.gentics.mesh.core.TypeInfo;
import com.gentics.mesh.core.data.Branch;
import com.gentics.mesh.core.data.NodeGraphFieldContainer;
import com.gentics.mesh.core.data.User;
import com.gentics.mesh.core.data.node.Node;
import com.gentics.mesh.core.rest.common.ContainerType;
import com.gentics.mesh.core.rest.schema.SchemaModel;
import com.gentics.mesh.core.rest.schema.SchemaReference;
import com.gentics.mesh.core.rest.schema.impl.SchemaResponse;
import com.gentics.mesh.madl.traversal.TraversalResult;
import com.gentics.mesh.search.index.Bucket;

/**
 * Each schema update is stored within a dedicated schema container version in order to be able to keep track of changes in between different schema container
 * versions.
 */
public interface SchemaContainerVersion
		extends GraphFieldSchemaContainerVersion<SchemaResponse, SchemaModel, SchemaReference, SchemaContainerVersion, SchemaContainer> {

	static final TypeInfo TYPE_INFO = new TypeInfo(SCHEMAVERSION, SCHEMA_CREATED, SCHEMA_UPDATED, SCHEMA_DELETED);

	@Override
	default TypeInfo getTypeInfo() {
		return TYPE_INFO;
	}

	/**
	 * Return a stream for {@link NodeGraphFieldContainer}'s that use this schema version and are versions for the given branch.
	 *
	 * @param branchUuid
	 *            branch Uuid
	 * @return
	 */
	Stream<? extends NodeGraphFieldContainer> getFieldContainers(String branchUuid);

	/**
	 * Return a stream for {@link NodeGraphFieldContainer}'s that use this schema version, are versions of the given branch and are listed within the given bucket.
	 * @param branchUuid
	 * @param bucket
	 * @return
	 */
	Stream<? extends NodeGraphFieldContainer> getFieldContainers(String branchUuid, Bucket bucket);

	/**
	 * Returns an iterator for those {@link NodeGraphFieldContainer}'s which can be edited by users. Those are draft and publish versions.
	 *
	 * @param branchUuid Branch Uuid
	 * @return
	 */
	default Iterator<? extends NodeGraphFieldContainer> getDraftFieldContainers(String branchUuid) {
		return getDraftFieldContainers(branchUuid, -1);
	}

	/**
	 * Returns an iterator for those {@link NodeGraphFieldContainer}'s which can be edited by users. Those are draft and publish versions. Results limit can be applied as well.
	 *
	 * @param branchUuid Branch Uuid
	 * @param limit limits the fetched vertices number. if less than 1, limits are disabled
	 * @return
	 */
	Iterator<? extends NodeGraphFieldContainer> getDraftFieldContainers(String branchUuid, int limit);

	/**
	 * Returns all nodes that the user has read permissions for.
	 *
	 * @param branchUuid Branch uuid
	 * @param user User to check permissions for
	 * @param type Container type
	 * @return
	 */
	TraversalResult<? extends Node> getNodes(String branchUuid, User user, ContainerType type);

	/**
	 * Check whether versioning is disabled by default or via the schema setting.
	 * @return
	 */
	boolean isAutoPurgeEnabled();

	/**
	 * Get the hash over all uuids of microschema versions, which are currently assigned to the branch and which are used in the schema version.
	 * A microschema is "used" by a schema, if the schema contains a field of type "microschema", where the microschema is mentioned in the "allowed" property.
	 * @param branch branch
	 * @return hash
	 */
	default String getMicroschemaVersionHash(Branch branch) {
		return getMicroschemaVersionHash(branch, Collections.emptyMap());
	}

	/**
	 * Variant of {@link #getMicroschemaVersionHash(Branch)}, that gets a replacement map (which might be empty, but not null). The replacement map may map microschema names
	 * to microschema version uuids to be used instead of the currently assigned microschema version.
	 * @param branch branch
	 * @param replacementMap replacement map
	 * @return hash
	 */
	String getMicroschemaVersionHash(Branch branch, Map<String, String> replacementMap);

	/**
	 * Get the name of fields, using the microschema.
	 * A field uses the microschema, if it is either a of type "microschema" or of type "list of microschemas" and mentions the microschema in the "allowed" property.
	 * @param microschema microschema in question
	 * @return set of field names
	 */
	Set<String> getFieldsUsingMicroschema(MicroschemaContainer microschema);

	/**
	 * Check whether the schema uses the given microschema.
	 * A microschema is "used" by a schema, if the schema contains a field of type "microschema", where the microschema is mentioned in the "allowed" property.
	 * @param microschema microschema in question
	 * @return true, when the schema version uses the microschema, false if not
	 */
	default boolean usesMicroschema(MicroschemaContainer microschema) {
		return !getFieldsUsingMicroschema(microschema).isEmpty();
	}
}
