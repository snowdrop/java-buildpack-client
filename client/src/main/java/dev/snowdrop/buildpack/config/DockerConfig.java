package dev.snowdrop.buildpack.config;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;

import dev.snowdrop.buildpack.BuildpackException;
import dev.snowdrop.buildpack.docker.DockerClientUtils;
import io.sundr.builder.annotations.Buildable;

@Buildable(generateBuilderPackage=true, builderPackage="dev.snowdrop.buildpack.builder")
public class DockerConfig {
    private static final Logger log = LoggerFactory.getLogger(DockerConfig.class);

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
    private HostAndSocketConfig hostAndSocketConfig;
    private String dockerNetwork;
    private Boolean useDaemon;
    private DockerClient dockerClient;
    private List<RegistryAuthConfig> authConfigs;

    public DockerConfig(                   
        Integer pullTimeoutSeconds, 
        Integer pullRetryCount,
        Integer pullRetryIncreaseSeconds,
        PullPolicy pullPolicy,
        HostAndSocketConfig hostAndSocketConfig,
        String dockerNetwork,
        Boolean useDaemon, 
        DockerClient dockerClient,
        List<RegistryAuthConfig> authConfigs
    ){
        log.debug("DockerConfig: pullTimeoutSeconds={}, pullRetryCount={}, pullRetryIncreaseSeconds={}, pullPolicy={}, hostAndSocketConfig={}, dockerNetwork={}, useDaemon={}", 
            pullTimeoutSeconds, pullRetryCount, pullRetryIncreaseSeconds, pullPolicy, hostAndSocketConfig, dockerNetwork, useDaemon);  

        this.pullTimeoutSeconds = pullTimeoutSeconds != null ? Integer.max(0,pullTimeoutSeconds) : DEFAULT_PULL_TIMEOUT;
        this.pullRetryCount = pullRetryCount != null ? Integer.max(0,pullRetryCount) : DEFAULT_PULL_RETRY_COUNT;
        this.pullRetryIncreaseSeconds = pullRetryIncreaseSeconds != null ? Integer.max(0,pullRetryIncreaseSeconds) : DEFAULT_PULL_RETRY_INCREASE;
        this.pullPolicy = pullPolicy != null ? pullPolicy : DEFAULT_PULL_POLICY;
        this.dockerNetwork = dockerNetwork;
        this.useDaemon = useDaemon != null ? useDaemon : Boolean.TRUE; //default daemon to true for back compat.

        //process host & socket passed, and probe runtime to fill in unset values.
        setHostAndSocketConfig(hostAndSocketConfig);

        this.authConfigs = authConfigs == null ? new ArrayList<>() : authConfigs;
        this.dockerClient = dockerClient;
        
    }

    public void setHostAndSocketConfig(HostAndSocketConfig hostAndSocketConfig) {
        this.hostAndSocketConfig = DockerClientUtils.probeContainerRuntime(hostAndSocketConfig);
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

    public HostAndSocketConfig getHostAndSocketConfig(){
        return this.hostAndSocketConfig;
    }

    public String getDockerNetwork(){
        return this.dockerNetwork;
    }

    public DockerClient getDockerClient(){
        this.dockerClient = this.dockerClient != null ? this.dockerClient : DockerClientUtils.getDockerClient(this.hostAndSocketConfig, this.authConfigs);
        return this.dockerClient;
    }

    public Boolean getUseDaemon(){
        return this.useDaemon;
    }

    public List<RegistryAuthConfig> getAuthConfigs(){
        return this.authConfigs;
    }
}

