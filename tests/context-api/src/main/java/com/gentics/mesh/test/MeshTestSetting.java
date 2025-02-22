package com.gentics.mesh.test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.gentics.mesh.test.MeshOptionChanger.NoOptionChanger;

/**
 * 
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface MeshTestSetting {

	/**
	 * Flag which indicates whether the ES docker container should be started.
	 * 
	 * @return
	 */
	ElasticsearchTestMode elasticsearch() default ElasticsearchTestMode.NONE;

	/**
	 * Flag which indicates whether the AWS docker container should be started.
	 *
	 * @return
	 */
	AWSTestMode awsContainer() default AWSTestMode.NONE;

	/**
	 * Setting which indicates what size of test data should be created.
	 * 
	 * @return
	 */
	TestSize testSize() default TestSize.PROJECT;

	/**
	 * Flag which indicates whether the mesh http server should be started.
	 * 
	 * @return
	 */
	boolean startServer() default false;

	/**
	 * Flag which indicates whether the graph database should run in-memory mode.
	 * 
	 * @return
	 */
	boolean inMemoryDB() default true;

	/**
	 * Flag which indicates whether the graph database server should be started (only if {@link #inMemoryDB()} is false)
	 * 
	 * @return
	 */
	boolean startStorageServer() default false;

	/**
	 * Flag which indicates whether the keycloak server should be used.
	 * 
	 * @return
	 */
	boolean useKeycloak() default false;

	/**
	 * Flag which indicates that the cluster mode should be enabled.
	 * 
	 * @return
	 */
	boolean clusterMode() default false;

	/**
	 * SSL test mode.
	 * 
	 * @return
	 */
	SSLTestMode ssl() default SSLTestMode.OFF;

	/**
	 * Flag which indicates whether the monitoring feature should be enabled.
	 * 
	 * @return
	 */
	boolean monitoring() default true;

	/**
	 * Predefined set of options changers that can be used to alter the mesh options for the test.
	 * 
	 * @return
	 */
	MeshCoreOptionChanger optionChanger() default MeshCoreOptionChanger.NO_CHANGE;

	Class<? extends MeshOptionChanger> customOptionChanger() default NoOptionChanger.class;
}
