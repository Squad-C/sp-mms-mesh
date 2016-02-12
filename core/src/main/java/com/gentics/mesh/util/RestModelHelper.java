package com.gentics.mesh.util;

import static com.gentics.mesh.core.data.relationship.GraphPermission.READ_PERM;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.gentics.mesh.core.data.Role;
import com.gentics.mesh.core.data.container.impl.MicroschemaContainerImpl;
import com.gentics.mesh.core.data.relationship.GraphPermission;
import com.gentics.mesh.core.data.root.impl.MeshRootImpl;
import com.gentics.mesh.core.data.schema.impl.SchemaContainerImpl;
import com.gentics.mesh.core.rest.schema.Microschema;
import com.gentics.mesh.core.rest.schema.Schema;
import com.gentics.mesh.handler.InternalActionContext;

public final class RestModelHelper {

	private RestModelHelper() {
	}

	public static void setRolePermissions(InternalActionContext ac, SchemaContainerImpl sourceElement, Schema restSchema) {
		String rolePermissionParameter = ac.getRolePermissionParameter();

		if (!isEmpty(rolePermissionParameter)) {
			Role role = MeshRootImpl.getInstance().getRoleRoot().loadObjectByUuid(ac, rolePermissionParameter, READ_PERM).toBlocking().first();
			if (role != null) {
				Set<GraphPermission> permSet = role.getPermissions(sourceElement);
				Set<String> humanNames = new HashSet<>();
				for (GraphPermission permission : permSet) {
					humanNames.add(permission.getSimpleName());
				}
				String[] names = humanNames.toArray(new String[humanNames.size()]);
				restSchema.setRolePerms(names);
			}
		}

	}

	public static void setRolePermissions(InternalActionContext ac, MicroschemaContainerImpl sourceElement, Microschema restSchema) {
		String rolePermissionParameter = ac.getRolePermissionParameter();

		if (!StringUtils.isEmpty(rolePermissionParameter)) {
			Role role = MeshRootImpl.getInstance().getRoleRoot().loadObjectByUuid(ac, rolePermissionParameter, READ_PERM).toBlocking().first();
			if (role != null) {
				Set<GraphPermission> permSet = role.getPermissions(sourceElement);
				Set<String> humanNames = new HashSet<>();
				for (GraphPermission permission : permSet) {
					humanNames.add(permission.getSimpleName());
				}
				String[] names = humanNames.toArray(new String[humanNames.size()]);
				restSchema.setRolePerms(names);
			}
		}
	}
}
