package com.gentics.mesh.core.data.schema;

import java.util.Map;

import com.gentics.mesh.context.InternalActionContext;
import com.gentics.mesh.core.data.HibCoreElement;
import com.gentics.mesh.core.data.HibNamedElement;
import com.gentics.mesh.core.data.HibReferenceableElement;
import com.gentics.mesh.core.data.branch.HibBranch;
import com.gentics.mesh.core.data.dao.DaoTransformable;
import com.gentics.mesh.core.data.user.HibUserTracking;
import com.gentics.mesh.core.rest.common.NameUuidReference;
import com.gentics.mesh.core.rest.schema.FieldSchemaContainer;
import com.gentics.mesh.core.rest.schema.FieldSchemaContainerVersion;
import com.gentics.mesh.util.ETag;

/**
 * Common interfaces shared by schema and microschema versions
 */
public interface HibFieldSchemaElement<
			R extends FieldSchemaContainer, 
			RM extends FieldSchemaContainerVersion, 
			RE extends NameUuidReference<RE>, 
			SC extends HibFieldSchemaElement<R, RM, RE, SC, SCV>, 
			SCV extends HibFieldSchemaVersionElement<R, RM, RE, SC, SCV>
	> extends HibCoreElement<R>, HibReferenceableElement<RE>, HibUserTracking, HibNamedElement {

	/**
	 * Return the latest container version.
	 * 
	 * @return Latest version
	 */
	SCV getLatestVersion();

	/**
	 * Set the latest container version.
	 * 
	 * @param version
	 */
	void setLatestVersion(SCV version);

	/**
	 * Return a map of all branches which reference the container via an assigned container version. The found container version will be added as key to the
	 * map.
	 * 
	 * @deprecated move this to DAO
	 * @return
	 */
	Map<HibBranch, SCV> findReferencedBranches();

	/**
	 * Transform a container to its REST representation.
	 * 
	 * @see DaoTransformable#transformToRestSync(Object, InternalActionContext, int, String...)
	 * @param ac
	 * @param level
	 * @param languageTags
	 * @return
	 */
	R transformToRestSync(InternalActionContext ac, int level, String... languageTags);

	@Override
	default String getSubETag(InternalActionContext ac) {
		return ETag.hash(getLatestVersion().getETag(ac));
	}
}
