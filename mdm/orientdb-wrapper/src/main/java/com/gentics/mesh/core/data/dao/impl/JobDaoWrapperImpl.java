package com.gentics.mesh.core.data.dao.impl;

import static com.gentics.mesh.core.data.util.HibClassConverter.toGraph;

import java.time.ZonedDateTime;
import java.util.function.Predicate;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.gentics.mesh.cli.OrientDBBootstrapInitializer;
import com.gentics.mesh.context.BulkActionContext;
import com.gentics.mesh.context.InternalActionContext;
import com.gentics.mesh.core.data.branch.HibBranch;
import com.gentics.mesh.core.data.dao.AbstractCoreDaoWrapper;
import com.gentics.mesh.core.data.dao.JobDaoWrapper;
import com.gentics.mesh.core.data.job.HibJob;
import com.gentics.mesh.core.data.job.Job;
import com.gentics.mesh.core.data.page.Page;
import com.gentics.mesh.core.data.project.HibProject;
import com.gentics.mesh.core.data.root.RootVertex;
import com.gentics.mesh.core.data.schema.HibMicroschemaVersion;
import com.gentics.mesh.core.data.schema.HibSchemaVersion;
import com.gentics.mesh.core.data.user.HibUser;
import com.gentics.mesh.core.rest.job.JobResponse;
import com.gentics.mesh.core.result.Result;
import com.gentics.mesh.parameter.PagingParameters;

import dagger.Lazy;

/**
 * DAO for jobs.
 */
@Singleton
public class JobDaoWrapperImpl extends AbstractCoreDaoWrapper<JobResponse, HibJob, Job> implements JobDaoWrapper {

	@Inject
	public JobDaoWrapperImpl(Lazy<OrientDBBootstrapInitializer> boot) {
		super(boot);
	}

	@Override
	public Result<? extends HibJob> findAll() {
		return boot.get().meshRoot().getJobRoot().findAll();
	}

	@Override
	public Page<? extends HibJob> findAll(InternalActionContext ac, PagingParameters pagingInfo, Predicate<HibJob> extraFilter) {
		return boot.get().meshRoot().getJobRoot().findAll(ac, pagingInfo, job -> {
			return extraFilter.test(job);
		});
	}

	@Override
	public HibJob findByName(String name) {
		return boot.get().meshRoot().getJobRoot().findByName(name);
	}

	@Override
	public HibJob enqueueSchemaMigration(HibUser creator, HibBranch branch, HibSchemaVersion fromVersion, HibSchemaVersion toVersion) {
		return boot.get().meshRoot().getJobRoot().enqueueSchemaMigration(creator, branch, fromVersion, toVersion);
	}

	@Override
	public HibJob enqueueBranchMigration(HibUser creator, HibBranch branch, HibSchemaVersion fromVersion, HibSchemaVersion toVersion) {
		return boot.get().meshRoot().getJobRoot().enqueueBranchMigration(creator, branch, fromVersion, toVersion);
	}

	@Override
	public HibJob enqueueMicroschemaMigration(HibUser creator, HibBranch branch, HibMicroschemaVersion fromVersion,
		HibMicroschemaVersion toVersion) {
		return boot.get().meshRoot().getJobRoot().enqueueMicroschemaMigration(creator, branch, fromVersion, toVersion);
	}

	@Override
	public HibJob enqueueBranchMigration(HibUser creator, HibBranch branch) {
		return boot.get().meshRoot().getJobRoot().enqueueBranchMigration(creator, branch);
	}

	@Override
	public void delete(HibJob job, BulkActionContext bac) {
		toGraph(job).delete(bac);
	}

	@Override
	public void clear() {
		boot.get().meshRoot().getJobRoot().clear();
	}

	@Override
	public long count() {
		return boot.get().meshRoot().getJobRoot().computeCount();
	}

	@Override
	public HibJob findByUuid(String uuid) {
		return boot.get().meshRoot().getJobRoot().findByUuid(uuid);
	}

	@Override
	public Page<? extends HibJob> findAllNoPerm(InternalActionContext ac, PagingParameters pagingInfo, Predicate<HibJob> extraFilter) {
		if (extraFilter == null) {
			return boot.get().meshRoot().getJobRoot().findAllNoPerm(ac, pagingInfo);
		} else {
			return boot.get().meshRoot().getJobRoot().findAllNoPerm(ac, pagingInfo, job -> {
				return extraFilter.test(job);
			});
		}
	}

	@Override
	public HibJob enqueueVersionPurge(HibUser user, HibProject project, ZonedDateTime before) {
		return boot.get().meshRoot().getJobRoot().enqueueVersionPurge(user, project, before);
	}

	@Override
	public HibJob enqueueVersionPurge(HibUser user, HibProject project) {
		return boot.get().meshRoot().getJobRoot().enqueueVersionPurge(user, project);
	}

	@Override
	public void purgeFailed() {
		boot.get().meshRoot().getJobRoot().purgeFailed();
	}

	@Override
	public void deleteByProject(HibProject project) {
		boot.get().meshRoot().getJobRoot().deleteByProject(toGraph(project));
	}

	@Override
	protected RootVertex<Job> getRoot() {
		return boot.get().meshRoot().getJobRoot();
	}
}
