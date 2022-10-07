package com.gentics.mesh.rest.client.method;

import com.gentics.mesh.core.rest.common.ObjectPermissionRequest;
import com.gentics.mesh.core.rest.common.ObjectPermissionResponse;
import com.gentics.mesh.core.rest.tag.TagFamilyCreateRequest;
import com.gentics.mesh.core.rest.tag.TagFamilyListResponse;
import com.gentics.mesh.core.rest.tag.TagFamilyResponse;
import com.gentics.mesh.core.rest.tag.TagFamilyUpdateRequest;
import com.gentics.mesh.parameter.PagingParameters;
import com.gentics.mesh.parameter.ParameterProvider;
import com.gentics.mesh.rest.client.MeshRequest;
import com.gentics.mesh.rest.client.impl.EmptyResponse;

/**
 * REST client methods which handle tag families
 */
public interface TagFamilyClientMethods {

	/**
	 * Load the tag family using the given UUID.
	 * 
	 * @param projectName
	 *            Project name
	 * @param uuid
	 *            Uuid of the tag family
	 * @param parameters
	 *            Additional query parameters
	 * @return
	 */
	MeshRequest<TagFamilyResponse> findTagFamilyByUuid(String projectName, String uuid, ParameterProvider... parameters);

	/**
	 * Load multiple tag families.
	 * 
	 * @param projectName
	 *            Project name
	 * @param pagingInfo
	 * @return
	 */
	MeshRequest<TagFamilyListResponse> findTagFamilies(String projectName, PagingParameters pagingInfo);

	/**
	 * Create a new tag family.
	 * 
	 * @param projectName
	 *            Project name
	 * @param request
	 *            Create Request
	 * @return
	 */
	MeshRequest<TagFamilyResponse> createTagFamily(String projectName, TagFamilyCreateRequest request);

	/**
	 * Delete the tag family.
	 *
	 * @param projectName Name of the project
	 * @param uuid        Uuid of the tag family
	 * @return
	 */
	MeshRequest<EmptyResponse> deleteTagFamily(String projectName, String uuid);

	/**
	 * Update the tag family.
	 * 
	 * @param projectName
	 * @param tagFamilyUuid
	 *            Uuid of the tag family
	 * @param request
	 *            Update request
	 * @return
	 */
	MeshRequest<TagFamilyResponse> updateTagFamily(String projectName, String tagFamilyUuid, TagFamilyUpdateRequest request);

	/**
	 * Create the tag family with the given uuid.
	 * 
	 * @param projectName
	 * @param tagFamilyUuid
	 *            Uuid of the tag family
	 * @param request
	 *            Create request
	 * @return
	 */
	MeshRequest<TagFamilyResponse> createTagFamily(String projectName, String tagFamilyUuid, TagFamilyCreateRequest request);

	/**
	 * Load multiple tag families.
	 * 
	 * @param projectName
	 * @param parameters
	 *            Additional query parameters
	 * @return
	 */
	MeshRequest<TagFamilyListResponse> findTagFamilies(String projectName, ParameterProvider... parameters);

	/**
	 * Get the role permissions on the tag family
	 * 
	 * @param projectName
	 *            Name of the project
	 * @param tagFamilyUuid
	 *            Uuid of the tagfamily
	 * @return request
	 */
	MeshRequest<ObjectPermissionResponse> getTagFamilyRolePermissions(String projectName, String tagFamilyUuid);

	/**
	 * Grant permissions on the tag family to roles
	 * 
	 * @param projectName
	 *            Name of the project
	 * @param tagFamilyUuid
	 *            Uuid of the tagfamily
	 * @param request request
	 * @return mesh request
	 */
	MeshRequest<ObjectPermissionResponse> grantTagFamilyRolePermissions(String projectName, String tagFamilyUuid, ObjectPermissionRequest request);

}
