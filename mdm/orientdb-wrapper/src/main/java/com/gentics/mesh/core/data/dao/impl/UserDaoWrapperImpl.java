package com.gentics.mesh.core.data.dao.impl;

import static com.gentics.mesh.core.data.relationship.GraphRelationships.ASSIGNED_TO_ROLE;
import static com.gentics.mesh.core.data.util.HibClassConverter.toGraph;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;

import javax.inject.Inject;

import com.gentics.mesh.cli.OrientDBBootstrapInitializer;
import com.gentics.mesh.context.InternalActionContext;
import com.gentics.mesh.core.data.Group;
import com.gentics.mesh.core.data.HibBaseElement;
import com.gentics.mesh.core.data.User;
import com.gentics.mesh.core.data.dao.AbstractCoreDaoWrapper;
import com.gentics.mesh.core.data.dao.GroupDao;
import com.gentics.mesh.core.data.dao.UserDaoWrapper;
import com.gentics.mesh.core.data.group.HibGroup;
import com.gentics.mesh.core.data.page.Page;
import com.gentics.mesh.core.data.perm.InternalPermission;
import com.gentics.mesh.core.data.role.HibRole;
import com.gentics.mesh.core.data.root.RootVertex;
import com.gentics.mesh.core.data.user.HibUser;
import com.gentics.mesh.core.data.user.MeshAuthUser;
import com.gentics.mesh.core.db.GraphDBTx;
import com.gentics.mesh.core.db.Tx;
import com.gentics.mesh.core.rest.user.UserResponse;
import com.gentics.mesh.core.result.Result;
import com.gentics.mesh.parameter.PagingParameters;
import com.syncleus.ferma.FramedGraph;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Vertex;

import dagger.Lazy;

/**
 * @see UserDaoWrapper
 */
public class UserDaoWrapperImpl extends AbstractCoreDaoWrapper<UserResponse, HibUser, User> implements UserDaoWrapper {

	@Inject
	public UserDaoWrapperImpl(Lazy<OrientDBBootstrapInitializer> boot) {
		super(boot);
	}

	@Override
	public EnumSet<InternalPermission> getPermissionsForElementId(HibUser user, Object elementId) {
		EnumSet<InternalPermission> permissions = EnumSet.noneOf(InternalPermission.class);
		for (InternalPermission perm : InternalPermission.values()) {
			if (hasPermissionForElementId(user, elementId, perm)) {
				permissions.add(perm);
			}
		}
		return permissions;
	}

	@Override
	public void preparePermissionsForElementIds(HibUser user, Collection<Object> elementIds) {
		// empty implementation
	}

	/**
	 * Check whether the user has the given permission on the element
	 * @param user user
	 * @param elementId element ID
	 * @param permission queried permission
	 * @return true if the user is granted the permission, false if not
	 */
	protected boolean hasPermissionForElementId(HibUser user, Object elementId, InternalPermission permission) {
		FramedGraph graph = GraphDBTx.getGraphTx().getGraph();
		// Find all roles that are assigned to the user by checking the
		// shortcut edge from the index
		String idxKey = "e." + ASSIGNED_TO_ROLE + "_out";
		Iterable<Edge> roleEdges = graph.getEdges(idxKey.toLowerCase(), user.getId());
		Vertex vertex = graph.getVertex(elementId);
		for (Edge roleEdge : roleEdges) {
			Vertex role = roleEdge.getVertex(Direction.IN);

			Set<String> allowedRoles = vertex.getProperty(permission.propertyKey());
			boolean hasPermission = allowedRoles != null && allowedRoles.contains(role.<String>getProperty("uuid"));
			if (hasPermission) {
				return true;
			}
		}

		return false;
	}

	@Override
	public Result<? extends User> findAll() {
		return getRoot().findAll();
	}

	@Override
	public Page<? extends HibUser> findAll(InternalActionContext ac, PagingParameters pagingInfo, Predicate<HibUser> extraFilter) {
		return boot.get().meshRoot().getUserRoot().findAllWrapped(ac, pagingInfo, extraFilter);
	}

	@Override
	public User findByName(String name) {
		return getRoot().findByName(name);
	}

	@Override
	public HibUser findByUsername(String username) {
		return boot.get().meshRoot().getUserRoot().findByUsername(username);
	}

	@Override
	public HibUser findByUuid(String uuid) {
		return getRoot().findByUuid(uuid);
	}

	@Override
	public MeshAuthUser findMeshAuthUserByUsername(String username) {
		return boot.get().meshRoot().getUserRoot().findMeshAuthUserByUsername(username);
	}

	@Override
	public MeshAuthUser findMeshAuthUserByUuid(String userUuid) {
		return boot.get().meshRoot().getUserRoot().findMeshAuthUserByUuid(userUuid);
	}

	@Override
	public HibUser inheritRolePermissions(HibUser user, HibBaseElement source, HibBaseElement target) {
		for (InternalPermission perm : InternalPermission.values()) {
			String key = perm.propertyKey();
			toGraph(target).property(key, toGraph(source).property(key));
		}
		Tx.get().permissionCache().clear();
		return user;
	}

	@Override
	public HibUser addGroup(HibUser user, HibGroup group) {
		User graphUser = toGraph(user);
		Group graphGroup = toGraph(group);
		graphUser.addGroup(graphGroup);
		return user;
	}

	/**
	 * Return the global amount of users stored in mesh.
	 */
	public long count() {
		return getRoot().globalCount();
	}

	@Override
	public Result<? extends HibGroup> getGroups(HibUser user) {
		User graphUser = toGraph(user);
		return graphUser.getGroups();
	}

	@Override
	public Iterable<? extends HibRole> getRoles(HibUser user) {
		User graphUser = toGraph(user);
		return graphUser.getRoles();
	}

	@Override
	public Page<? extends HibRole> getRolesViaShortcut(HibUser fromUser, HibUser authUser, PagingParameters pagingInfo) {
		return toGraph(fromUser).getRolesViaShortcut(authUser, pagingInfo);
	}

	@Override
	public Page<? extends HibGroup> getGroups(HibUser fromUser, HibUser authUser, PagingParameters pagingInfo) {
		return toGraph(fromUser).getGroups(authUser, pagingInfo);
	}

	@Override
	protected RootVertex<User> getRoot() {
		return boot.get().meshRoot().getUserRoot();
	}

	@Override
	public String getRolesHash(HibUser user) {
		return toGraph(user).getRolesHash();
	}

	@Override
	public void updateShortcutEdges(HibUser user) {
		GroupDao groupRoot = Tx.get().groupDao();
		User graph = toGraph(user);
		graph.outE(ASSIGNED_TO_ROLE).removeAll();
		for (HibGroup group : graph.getGroups()) {
			for (HibRole role : groupRoot.getRoles(group)) {
				graph.setUniqueLinkOutTo(toGraph(role), ASSIGNED_TO_ROLE);
			}
		}
	}
}
