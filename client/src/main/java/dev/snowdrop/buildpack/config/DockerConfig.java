package dev.snowdrop.buildpack.config;

import com.github.dockerjava.api.DockerClient;

import dev.snowdrop.buildpack.BuildpackException;
import dev.snowdrop.buildpack.docker.DockerClientUtils;
import io.sundr.builder.annotations.Buildable;

@Buildable(generateBuilderPackage=true, builderPackage="dev.snowdrop.buildpack.builder")
public class DockerConfig {
    public static DockerConfigBuilder builder() {
        return new DockerConfigBuilder();
    }

    private static final Integer DEFAULT_PULL_TIMEOUT = 60;
    
    private Integer pullTimeoutSeconds;
    private String dockerHost;
    private String dockerSocket;
    private String dockerNetwork;
    private Boolean useDaemon;
    private DockerClient dockerClient;

    public DockerConfig(                   
        Integer pullTimeoutSeconds, 
        String dockerHost, 
        String dockerSocket,
        String dockerNetwork,
        Boolean useDaemon, 
        DockerClient dockerClient
    ){
        this.pullTimeoutSeconds = pullTimeoutSeconds != null ? pullTimeoutSeconds : DEFAULT_PULL_TIMEOUT;
        this.dockerHost = dockerHost != null ? dockerHost : DockerClientUtils.getDockerHost();
        this.dockerSocket = dockerSocket != null ? dockerSocket : (this.dockerHost.startsWith("unix://") ? this.dockerHost.substring("unix://".length()) : "/var/run/docker.sock");
        this.dockerNetwork = dockerNetwork;
        this.useDaemon = useDaemon != null ? useDaemon : Boolean.TRUE; //default daemon to true for back compat.
        this.dockerClient = dockerClient != null ? dockerClient : DockerClientUtils.getDockerClient(this.dockerHost);

        try{
            this.dockerClient.pingCmd().exec();
        }catch(Exception e){
            throw new BuildpackException("Unable to verify docker settings", e);
        }
    }

    public Integer getPullTimeout(){
        return this.pullTimeoutSeconds;
    }

    public String getDockerHost(){
        return this.dockerHost;
    }

    public String getDockerSocket(){
        return this.dockerSocket;
    }

    public String getDockerNetwork(){
        return this.dockerNetwork;
    }

    public DockerClient getDockerClient(){
        return this.dockerClient;
    }

    public Boolean getUseDaemon(){
        return this.useDaemon;
    }
}

