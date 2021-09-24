package com.gentics.mesh.core.data.dao;

import com.gentics.mesh.core.data.HibBaseElement;
import com.gentics.mesh.core.data.MeshVertex;
import com.gentics.mesh.core.data.NodeGraphFieldContainer;
import com.gentics.mesh.core.data.perm.InternalPermission;
import com.gentics.mesh.core.data.user.HibUser;

/**
 * User DAO
 */
public interface UserDaoWrapper extends UserDao {

	/**
	 * Check whether the user has the given permission on the given element.
	 *
	 * @param user
	 * @param element
	 * @param permission
	 * @return
	 * @deprecated Use {@link #hasPermission(HibUser, HibBaseElement, InternalPermission)} instead.
	 */
	@Deprecated
	boolean hasPermission(HibUser user, MeshVertex element, InternalPermission permission);

	/**
	 * Check the read permission on the given container and return false if the needed permission to read the container is not set. This method will not return
	 * false if the user has READ permission or READ_PUBLISH permission on a published node.
	 *
	 * @param user
	 * @param container
	 * @param branchUuid
	 * @param requestedVersion
	 */
	boolean hasReadPermission(HibUser user, NodeGraphFieldContainer container, String branchUuid, String requestedVersion);

	/**
	 * Check the read permission on the given container and fail if the needed permission to read the container is not set. This method will not fail if the
	 * user has READ permission or READ_PUBLISH permission on a published node.
	 *
	 * @param container
	 * @param branchUuid
	 * @param requestedVersion
	 */
	void failOnNoReadPermission(HibUser user, NodeGraphFieldContainer container, String branchUuid, String requestedVersion);

}
