package com.redhat.buildpack.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.apache.commons.lang.SystemUtils;

public class DockerClientUtils {

	public static DockerClient getDockerClient(){
		return getDockerClient(getDockerHost());
	}

	/**
	 * Simple util to get a DockerClient for the platform. 
	 * probably needs more work for other platforms, and we may 
	 * want a way to configure authentication etc. 
	 */
	public static DockerClient getDockerClient(String dockerHost) {	
		System.out.println("Using dockerhost "+dockerHost);

		DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
				.withDockerHost(dockerHost)
				.build();

		DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
			    .dockerHost(config.getDockerHost())
			    .sslConfig(config.getSSLConfig())
				.build();
				
		DockerClient dockerClient = DockerClientImpl.getInstance(config, httpClient);
		
		return dockerClient;
	}

	public static String getDockerHost() {
		String dockerHost = System.getenv("DOCKER_HOST");
		if(dockerHost==null) {
			if(SystemUtils.IS_OS_WINDOWS){
				dockerHost = "npipe:////./pipe/docker_engine";
			}else {
				dockerHost = "unix:///var/run/docker.sock";
			}
		}
		return dockerHost;
	}
}
