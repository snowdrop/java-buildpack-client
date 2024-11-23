package dev.snowdrop.buildpack.config;

import java.util.ArrayList;
import java.util.List;

import com.github.dockerjava.api.DockerClient;

import dev.snowdrop.buildpack.BuildpackException;
import dev.snowdrop.buildpack.docker.DockerClientUtils;
import dev.snowdrop.buildpack.docker.DockerClientUtils.HostAndSocket;
import io.sundr.builder.annotations.Buildable;

@Buildable(generateBuilderPackage=true, builderPackage="dev.snowdrop.buildpack.builder")
public class DockerConfig {
    public static DockerConfigBuilder builder() {
        return new DockerConfigBuilder();
    }

    public static enum PullPolicy {ALWAYS, IF_NOT_PRESENT, NEVER};

    private static final Integer DEFAULT_PULL_TIMEOUT = 60;
    private static final Integer DEFAULT_PULL_RETRY_INCREASE = 15;
    private static final Integer DEFAULT_PULL_RETRY_COUNT = 3;
    private static final PullPolicy DEFAULT_PULL_POLICY = PullPolicy.IF_NOT_PRESENT;
    
    private Integer pullTimeoutSeconds;
    private Integer pullRetryCount;
    private Integer pullRetryIncreaseSeconds;
    private PullPolicy pullPolicy;
    private String dockerHost;
    private String dockerSocket;
    private String dockerNetwork;
    private Boolean useDaemon;
    private DockerClient dockerClient;
    private List<RegistryAuthConfig> authConfigs;

    public DockerConfig(                   
        Integer pullTimeoutSeconds, 
        Integer pullRetryCount,
        Integer pullRetryIncreaseSeconds,
        PullPolicy pullPolicy,
        String dockerHost, 
        String dockerSocket,
        String dockerNetwork,
        Boolean useDaemon, 
        DockerClient dockerClient,
        List<RegistryAuthConfig> authConfigs
    ){
        this.pullTimeoutSeconds = pullTimeoutSeconds != null ? Integer.max(0,pullTimeoutSeconds) : DEFAULT_PULL_TIMEOUT;
        this.pullRetryCount = pullRetryCount != null ? Integer.max(0,pullRetryCount) : DEFAULT_PULL_RETRY_COUNT;
        this.pullRetryIncreaseSeconds = pullRetryIncreaseSeconds != null ? Integer.max(0,pullRetryIncreaseSeconds) : DEFAULT_PULL_RETRY_INCREASE;
        this.pullPolicy = pullPolicy != null ? pullPolicy : DEFAULT_PULL_POLICY;
        this.dockerNetwork = dockerNetwork;
        this.useDaemon = useDaemon != null ? useDaemon : Boolean.TRUE; //default daemon to true for back compat.

        //take config values, and determine values to use.. 
        HostAndSocket hands = DockerClientUtils.probeContainerRuntime(new DockerClientUtils.HostAndSocket(dockerHost, dockerSocket));
        this.dockerHost = hands.host;
        this.dockerSocket = hands.socket;

        this.authConfigs = authConfigs == null ? new ArrayList<>() : authConfigs;

        this.dockerClient = dockerClient != null ? dockerClient : DockerClientUtils.getDockerClient(hands, authConfigs);

        try{
            this.dockerClient.pingCmd().exec();
        }catch(Exception e){
            throw new BuildpackException("Unable to verify docker settings", e);
        }
    }

    public void setDockerHost(String dockerHost){
        HostAndSocket hands = DockerClientUtils.probeContainerRuntime(new DockerClientUtils.HostAndSocket(dockerHost, this.dockerSocket));
        this.dockerHost = hands.host;
        this.dockerSocket = hands.socket;
        this.dockerClient = dockerClient != null ? dockerClient : DockerClientUtils.getDockerClient(hands);        
    }
    public void setDockerSocket(String dockerSocket){
        HostAndSocket hands = DockerClientUtils.probeContainerRuntime(new DockerClientUtils.HostAndSocket(this.dockerHost, dockerSocket));
        this.dockerHost = hands.host;
        this.dockerSocket = hands.socket;
        this.dockerClient = dockerClient != null ? dockerClient : DockerClientUtils.getDockerClient(hands);
    }

    public Integer getPullTimeoutSeconds(){
        return this.pullTimeoutSeconds;
    }

    public Integer getPullRetryCount(){
        return this.pullRetryCount;
    }

    public Integer getPullRetryIncreaseSeconds(){
        return this.pullRetryIncreaseSeconds;
    }

    public PullPolicy getPullPolicy(){
        return this.pullPolicy;
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

    public List<RegistryAuthConfig> getAuthConfigs(){
        return this.authConfigs;
    }
}

