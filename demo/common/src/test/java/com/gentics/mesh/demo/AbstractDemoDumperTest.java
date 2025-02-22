package com.gentics.mesh.demo;

import static com.gentics.mesh.core.rest.common.ContainerType.PUBLISHED;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.gentics.mesh.Mesh;
import com.gentics.mesh.cli.BootstrapInitializer;
import com.gentics.mesh.core.data.HibNodeFieldContainer;
import com.gentics.mesh.core.data.dao.BranchDao;
import com.gentics.mesh.core.data.dao.ContentDao;
import com.gentics.mesh.core.data.dao.GroupDao;
import com.gentics.mesh.core.data.dao.NodeDao;
import com.gentics.mesh.core.data.dao.ProjectDao;
import com.gentics.mesh.core.data.dao.RoleDao;
import com.gentics.mesh.core.data.dao.UserDao;
import com.gentics.mesh.core.data.group.HibGroup;
import com.gentics.mesh.core.data.node.HibNode;
import com.gentics.mesh.core.data.perm.InternalPermission;
import com.gentics.mesh.core.data.project.HibProject;
import com.gentics.mesh.core.data.role.HibRole;
import com.gentics.mesh.core.data.user.HibUser;
import com.gentics.mesh.core.db.Database;
import com.gentics.mesh.core.rest.common.ContainerType;
import com.gentics.mesh.dagger.MeshComponent;
import com.gentics.mesh.search.SearchProvider;
import com.gentics.mesh.test.MeshOptionsTypeUnawareContext;

public abstract class AbstractDemoDumperTest implements MeshOptionsTypeUnawareContext {

	private BootstrapInitializer boot;

	private SearchProvider searchProvider;

	private Database db;

	private Mesh mesh;

	protected static DemoDumper generator = null;

	@Before
	public void setup() throws Exception {
		MeshComponent internal = generator.getMeshInternal();
		boot = internal.boot();
		searchProvider = internal.searchProvider();
		db = internal.database();
	}

	@Test
	public void testSetup() throws Exception {
		generator.dump();
		db.tx(tx -> {
			RoleDao roleDao = tx.roleDao();
			UserDao userDao = tx.userDao();
			GroupDao groupDao = tx.groupDao();
			ProjectDao projectDao = tx.projectDao();
			ContentDao contentDao = tx.contentDao();
			BranchDao branchDao = tx.branchDao();
			NodeDao nodeDao = tx.nodeDao();

			HibProject project = projectDao.findByName("demo");
			assertTrue(nodeDao.count(project) > 0);
			HibUser user = userDao.findByUsername("webclient");
			assertNotNull("The webclient user should have been created but could not be found.", user);
			assertFalse("The webclient user should also have at least one group assigned to it.", !userDao.getGroups(user).iterator().hasNext());
			HibGroup group = userDao.getGroups(user).iterator().next();
			HibRole role = groupDao.getRoles(group).iterator().next();
			assertNotNull("The webclient group should also have a role assigned to it", role);

			assertTrue("The webclient role has no read permission on the user.", roleDao.hasPermission(role, InternalPermission.READ_PERM, user));
			assertTrue("The webclient user has no permission on itself.", userDao.hasPermission(user, user, InternalPermission.READ_PERM));
			assertTrue("The webclient user has no read permission on the user root node..", userDao.hasPermission(user, tx.data().permissionRoots().user(), InternalPermission.READ_PERM));

			assertTrue("We expected to find at least 5 nodes.", nodeDao.count(project) > 5);

			// Verify that the uuids have been updated
			assertNotNull(nodeDao.findByUuid(project, "df8beb3922c94ea28beb3922c94ea2f6"));

			// Verify that all documents are stored in the index
			for (HibNode node : nodeDao.findAll(project)) {
				HibNodeFieldContainer container = contentDao.getLatestDraftFieldContainer(node, "en");
				String languageTag = "en";
				String projectUuid = node.getProject().getUuid();
				String branchUuid = branchDao.getInitialBranch(node.getProject()).getUuid();
				String schemaContainerVersionUuid = container.getSchemaContainerVersion().getUuid();
				String microSchemaVersionHash = container.getSchemaContainerVersion().getMicroschemaVersionHash(branchDao.getInitialBranch(node.getProject()));
				ContainerType type = PUBLISHED;
				String indexName = ContentDao.composeIndexName(projectUuid, branchUuid, schemaContainerVersionUuid, type, null, microSchemaVersionHash);
				String documentId = ContentDao.composeDocumentId(node.getUuid(), languageTag);
				if (searchProvider.getDocument(indexName, documentId).blockingGet() == null) {
					String msg = "The search document for node {" + node.getUuid() + "} container {" + languageTag
						+ "} could not be found within index {" + indexName + "} - {" + documentId + "}";
					fail(msg);
				}
			}
			tx.success();
		});
	}

	@After
	public void shutdown() throws Exception {
		if (mesh != null) {
			mesh.shutdown();
		}
	}
}
