package com.gentics.mesh.core.data.job.impl;

import static com.gentics.mesh.core.data.perm.InternalPermission.READ_PERM;
import static com.gentics.mesh.core.data.relationship.GraphRelationships.HAS_JOB;
import static com.gentics.mesh.core.data.relationship.GraphRelationships.HAS_PROJECT;
import static com.gentics.mesh.core.rest.error.Errors.error;
import static com.gentics.mesh.core.rest.job.JobStatus.QUEUED;
import static com.gentics.mesh.madl.index.EdgeIndexDefinition.edgeIndex;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.function.Predicate;

import org.apache.commons.lang.NotImplementedException;

import com.gentics.madl.index.IndexHandler;
import com.gentics.madl.type.TypeHandler;
import com.gentics.mesh.context.BulkActionContext;
import com.gentics.mesh.context.InternalActionContext;
import com.gentics.mesh.core.data.HibBaseElement;
import com.gentics.mesh.core.data.Project;
import com.gentics.mesh.core.data.branch.HibBranch;
import com.gentics.mesh.core.data.generic.MeshVertexImpl;
import com.gentics.mesh.core.data.job.HibJob;
import com.gentics.mesh.core.data.job.Job;
import com.gentics.mesh.core.data.job.JobRoot;
import com.gentics.mesh.core.data.page.Page;
import com.gentics.mesh.core.data.page.impl.DynamicTransformablePageImpl;
import com.gentics.mesh.core.data.project.HibProject;
import com.gentics.mesh.core.data.root.impl.AbstractRootVertex;
import com.gentics.mesh.core.data.schema.HibMicroschemaVersion;
import com.gentics.mesh.core.data.schema.HibSchemaVersion;
import com.gentics.mesh.core.data.user.HibUser;
import com.gentics.mesh.core.db.GraphDBTx;
import com.gentics.mesh.core.rest.job.JobType;
import com.gentics.mesh.core.result.Result;
import com.gentics.mesh.parameter.PagingParameters;
import com.syncleus.ferma.FramedGraph;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Vertex;

/**
 * @see JobRoot
 */
public class JobRootImpl extends AbstractRootVertex<Job> implements JobRoot {

	/**
	 * Initialize the vertex type and index.
	 * 
	 * @param type
	 * @param index
	 */
	public static void init(TypeHandler type, IndexHandler index) {
		type.createVertexType(JobRootImpl.class, MeshVertexImpl.class);
		index.createIndex(edgeIndex(HAS_JOB).withInOut().withOut());
	}

	@Override
	public Class<? extends Job> getPersistanceClass() {
		return JobImpl.class;
	}

	@Override
	public String getRootLabel() {
		return HAS_JOB;
	}

	@Override
	public long globalCount() {
		return db().count(JobImpl.class);
	}

	/**
	 * Find the element with the given uuid.
	 * 
	 * @param uuid
	 *            Uuid of the element to be located
	 * @return Found element or null if the element could not be located
	 */
	public Job findByUuid(String uuid) {
		FramedGraph graph = GraphDBTx.getGraphTx().getGraph();
		// 1. Find the element with given uuid within the whole graph
		Iterator<Vertex> it = db().getVertices(MeshVertexImpl.class, new String[] { "uuid" }, new String[] { uuid });
		if (it.hasNext()) {
			Vertex potentialElement = it.next();
			// 2. Use the edge index to determine whether the element is part of this root vertex
			Iterable<Edge> edges = graph.getEdges("e." + getRootLabel().toLowerCase() + "_inout",
				db().index().createComposedIndexKey(potentialElement.getId(), id()));
			if (edges.iterator().hasNext()) {
				// Don't frame explicitly since multiple types can be returned
				return graph.frameElement(potentialElement, getPersistanceClass());
			}
		}
		return null;
	}

	@Override
	public Result<? extends Job> findAll() {
		// We need to enforce the usage of dynamic loading since the root->item yields different types of vertices.
		return super.findAllDynamic();
	}

	@Override
	public Job enqueueSchemaMigration(HibUser creator, HibBranch branch, HibSchemaVersion fromVersion, HibSchemaVersion toVersion) {
		NodeMigrationJobImpl job = getGraph().addFramedVertex(NodeMigrationJobImpl.class);
		job.setType(JobType.schema);
		job.setCreationTimestamp();
		job.setBranch(branch);
		job.setStatus(QUEUED);
		job.setFromSchemaVersion(fromVersion);
		job.setToSchemaVersion(toVersion);
		addItem(job);
		if (log.isDebugEnabled()) {
			log.debug("Enqueued schema migration job {" + job.getUuid() + "}");
		}
		return job;
	}

	@Override
	public Job enqueueMicroschemaMigration(HibUser creator, HibBranch branch, HibMicroschemaVersion fromVersion,
		HibMicroschemaVersion toVersion) {
		MicronodeMigrationJobImpl job = getGraph().addFramedVertex(MicronodeMigrationJobImpl.class);
		job.setType(JobType.microschema);
		job.setCreationTimestamp();
		job.setBranch(branch);
		job.setStatus(QUEUED);
		job.setFromMicroschemaVersion(fromVersion);
		job.setToMicroschemaVersion(toVersion);
		addItem(job);
		if (log.isDebugEnabled()) {
			log.debug("Enqueued microschema migration job {" + job.getUuid() + "} - " + toVersion.getSchemaContainer().getName() + " "
				+ fromVersion.getVersion() + " to " + toVersion.getVersion());
		}
		return job;
	}

	@Override
	public HibJob enqueueBranchMigration(HibUser creator, HibBranch branch, HibSchemaVersion fromVersion, HibSchemaVersion toVersion) {
		Job job = getGraph().addFramedVertex(BranchMigrationJobImpl.class);
		job.setType(JobType.branch);
		job.setCreationTimestamp();
		job.setBranch(branch);
		job.setStatus(QUEUED);
		job.setFromSchemaVersion(fromVersion);
		job.setToSchemaVersion(toVersion);
		addItem(job);
		if (log.isDebugEnabled()) {
			log.debug("Enqueued branch migration job {" + job.getUuid() + "} for branch {" + branch.getUuid() + "}");
		}
		return job;
	}

	@Override
	public Job enqueueBranchMigration(HibUser creator, HibBranch branch) {
		Job job = getGraph().addFramedVertex(BranchMigrationJobImpl.class);
		job.setType(JobType.branch);
		job.setCreationTimestamp();
		job.setStatus(QUEUED);
		job.setBranch(branch);
		addItem(job);
		if (log.isDebugEnabled()) {
			log.debug("Enqueued branch migration job {" + job.getUuid() + "} for branch {" + branch.getUuid() + "}");
		}
		return job;
	}

	@Override
	public Job enqueueVersionPurge(HibUser user, HibProject project, ZonedDateTime before) {
		VersionPurgeJobImpl job = getGraph().addFramedVertex(VersionPurgeJobImpl.class);
		// TODO Don't add the user to reduce contention
		// job.setCreated(user);
		job.setCreationTimestamp();
		job.setType(JobType.versionpurge);
		job.setStatus(QUEUED);
		job.setProject(project);
		job.setMaxAge(before);
		addItem(job);
		if (log.isDebugEnabled()) {
			log.debug("Enqueued project version purge job {" + job.getUuid() + "} for project {" + project.getName() + "}");
		}
		return job;
	}

	@Override
	public Job enqueueVersionPurge(HibUser user, HibProject project) {
		return enqueueVersionPurge(user, project, null);
	}

	@Override
	public HibBaseElement resolveToElement(HibBaseElement permissionRoot, HibBaseElement root, Stack<String> stack) {
		throw error(BAD_REQUEST, "Jobs are not accessible");
	}

	/**
	 * Find the visible elements and return a paged result.
	 * 
	 * @param ac
	 *            action context
	 * @param pagingInfo
	 *            Paging information object that contains page options.
	 * 
	 * @return
	 */
	public Page<? extends Job> findAll(InternalActionContext ac, PagingParameters pagingInfo) {
		return new DynamicTransformablePageImpl<>(ac.getUser(), this, pagingInfo, READ_PERM, null, false);
	}

	/**
	 * Find all elements and return a paged result. No permission check will be performed.
	 * 
	 * @param ac
	 *            action context
	 * @param pagingInfo
	 *            Paging information object that contains page options.
	 * 
	 * @return
	 */
	public Page<? extends Job> findAllNoPerm(InternalActionContext ac, PagingParameters pagingInfo) {
		return new DynamicTransformablePageImpl<>(ac.getUser(), this, pagingInfo, null, null, false);
	}

	@Override
	public Page<? extends Job> findAllNoPerm(InternalActionContext ac, PagingParameters pagingInfo, Predicate<Job> extraFilter) {
		return new DynamicTransformablePageImpl<>(ac.getUser(), this, pagingInfo, null, extraFilter, false);
	}

	@Override
	public void deleteByProject(Project project) {
		// note: it is very important to use has(VersionPurgeJobImpl.class) to check whether we actually get a job here
		// otherwise we would also get the ProjectRootImpl and delete it
		for (VersionPurgeJobImpl versionPurgeJob : project.in(HAS_PROJECT).has(VersionPurgeJobImpl.class)
				.frameExplicit(VersionPurgeJobImpl.class)) {
			versionPurgeJob.delete();
		}
	}

	@Override
	public void purgeFailed() {
		log.info("Purging failed jobs..");
		Iterable<? extends JobImpl> it = out(HAS_JOB).hasNot("error", null).frameExplicit(JobImpl.class);
		long count = 0;
		for (Job job : it) {
			job.delete();
			count++;
		}
		log.info("Purged {" + count + "} failed jobs.");
	}

	@Override
	public void clear() {
		out(HAS_JOB).removeAll();
	}

	@Override
	public void delete(BulkActionContext bac) {
		throw new NotImplementedException("The job root can't be deleted");
	}

	@Override
	public Optional<Set<Class<? extends Job>>> getPersistenceClassVariations() {
		return Optional.of(Set.of(BranchMigrationJobImpl.class, MicronodeMigrationJobImpl.class, NodeMigrationJobImpl.class, VersionPurgeJobImpl.class));
	}
}
