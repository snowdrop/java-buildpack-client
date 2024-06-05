package dev.snowdrop.buildpack.docker;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.snowdrop.buildpack.utils.OperatingSytem;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

public class DockerClientUtils {
  private static final Logger log = LoggerFactory.getLogger(DockerClientUtils.class);

  public static DockerClient getDockerClient() {
    return getDockerClient(getDockerHost());
  }

  /**
   * Simple util to get a DockerClient for the platform. probably needs more work
   * for other platforms, and we may want a way to configure authentication etc.
   */
  public static DockerClient getDockerClient(String dockerHost) {
    if (dockerHost == null || dockerHost.isEmpty()) {
      return getDockerClient(getDockerHost());
    }

    log.debug("Using dockerhost " + dockerHost);
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
    if (dockerHost != null && dockerHost.isEmpty()) {
      return dockerHost;
    }

    switch (OperatingSytem.getOperationSystem()) {
      case WIN:
        return "npipe:////./pipe/docker_engine";
      case LINUX: {
        //test for presence of docker.
        File dockerSock = new File("/var/run/docker.sock");
        if(dockerSock.exists()){
          return "unix:///var/run/docker.sock";
        }

        File podmanSock = new File("/var/run/podman.sock");
        if(podmanSock.exists()){
          return "unix:///var/run/podman.sock";
        }

        try{
          int uid = (Integer)Files.getAttribute(Paths.get("/proc/self"), "unix:uid");
          File podmanUserSock = new File("/var/run/user/"+uid+"/podman/podman.sock");
          if(podmanUserSock.exists()){
            return "unix:///var/run/user/"+uid+"/podman/podman.sock";
          }
        }catch(IOException io){
          //ignore.
        }
      }
    }

    //none of the known locations had socket files, default to docker
    //and assume the user has a plan we don't know about =)
    return "unix:///var/run/docker.sock";
  }
}
