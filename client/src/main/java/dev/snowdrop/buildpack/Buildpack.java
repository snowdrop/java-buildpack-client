package dev.snowdrop.buildpack;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.github.dockerjava.api.DockerClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.snowdrop.buildpack.docker.ContainerUtils;
import dev.snowdrop.buildpack.docker.Content;
import dev.snowdrop.buildpack.docker.DockerClientUtils;
import dev.snowdrop.buildpack.docker.ImageUtils;
import dev.snowdrop.buildpack.docker.ImageUtils.ImageInfo;
import dev.snowdrop.buildpack.phases.ContainerStatus;
import dev.snowdrop.buildpack.phases.LifecyclePhase;
import dev.snowdrop.buildpack.phases.LifecyclePhaseFactory;
import dev.snowdrop.buildpack.utils.BuildpackMetadata;
import io.sundr.builder.annotations.Buildable;


@Buildable(generateBuilderPackage=true, builderPackage="dev.snowdrop.buildpack.builder")
public class Buildpack {

  public static BuildpackBuilder builder() {
    return new BuildpackBuilder();
  }

  private static final Logger log = LoggerFactory.getLogger(Buildpack.class);

  private static final String DEFAULT_BUILD_IMAGE = "paketobuildpacks/builder:base";
  private static final Integer DEFAULT_PULL_TIMEOUT = 60;
  private static final String DEFAULT_LOG_LEVEL = "debug";

  // defaults for images
  private final String builderImage;
  private final String finalImage;
  private String runImage;

  private final Integer pullTimeoutSeconds;

  private final String dockerHost;
  private final String dockerSocket;
  private final String dockerNetwork;
  private final Boolean useDaemon;

  private final String buildCacheVolumeName;
  private final String buildCacheVolumePath;
  private final boolean removeBuildCacheAfterBuild;

  private final String launchCacheVolumeName;
  private final String launchCacheVolumePath;
  private final boolean removeLaunchCacheAfterBuild;

  private final String logLevel;
  private final boolean useTimestamps;

  private Integer userId;
  private Integer groupId;

  Map<String, String> environment = new HashMap<>();
  private List<Content> content = new LinkedList<>();
  private final DockerClient dockerClient;
  private final dev.snowdrop.buildpack.Logger logger;

  private final int exitCode;



  public Buildpack(String builderImage, 
                   String runImage, 
                   String finalImage, 
                   Integer pullTimeoutSeconds, 
                   String dockerHost, 
                   String dockerSocket,
                   String dockerNetwork,
                   Boolean useDaemon, 
                   String buildCacheVolumeName, 
                   String buildCacheVolumePath, 
                   boolean removeBuildCacheAfterBuild,
                   String launchCacheVolumeName, 
                   String launchCacheVolumePath, 
                   boolean removeLaunchCacheAfterBuild, 
                   String logLevel, 
                   boolean useTimestamps, 
                   Map<String, String> environment, 
                   List<Content> content, 
                   DockerClient dockerClient,
                   dev.snowdrop.buildpack.Logger logger) {

    this.builderImage = builderImage != null ? builderImage : DEFAULT_BUILD_IMAGE;
    this.runImage = runImage;
    this.finalImage = finalImage;
    this.pullTimeoutSeconds = pullTimeoutSeconds != null ? pullTimeoutSeconds : DEFAULT_PULL_TIMEOUT;
    this.dockerHost = dockerHost != null ? dockerHost : DockerClientUtils.getDockerHost();
    this.dockerNetwork = dockerNetwork;
    this.useDaemon = useDaemon != null ? useDaemon : Boolean.TRUE; //default daemon to true for back compat.
    this.buildCacheVolumeName = buildCacheVolumeName;
    this.buildCacheVolumePath = buildCacheVolumePath;
    this.removeBuildCacheAfterBuild = removeBuildCacheAfterBuild || buildCacheVolumeName!=null;
    this.launchCacheVolumeName = launchCacheVolumeName;
    this.launchCacheVolumePath = launchCacheVolumePath;
    this.removeLaunchCacheAfterBuild = removeLaunchCacheAfterBuild || launchCacheVolumeName!=null;
    this.logLevel = logLevel != null ? logLevel : DEFAULT_LOG_LEVEL;
    this.useTimestamps = useTimestamps;
    this.environment = environment != null ? environment : new HashMap<>();
    this.content = content;
    this.dockerClient = dockerClient != null ? dockerClient : DockerClientUtils.getDockerClient(dockerHost);
    this.logger = logger != null ? logger : new SystemLogger();
    // We still only support docker daemon execution, and if 
    // dockerHost is configured, then test if it is a unix:// path
    // and set dockerSocket path appropriately.
    this.dockerSocket = dockerSocket != null ? dockerSocket : (this.dockerHost.startsWith("unix://") ? this.dockerHost.substring("unix://".length()) : "/var/run/docker.sock");


    //run the build.
    this.exitCode = build();
  }

  private int build() {

    log.info("Buildpack build invoked, preparing environment...");
    prep();

    LifecyclePhaseFactory lifecycle = new LifecyclePhaseFactory(dockerClient, userId, groupId, this);

    //create and run the creator phase
    LifecyclePhase creator = lifecycle.getCreator();
    ContainerStatus cs = creator.runPhase(logger, useTimestamps);
   
    log.info("Buildpack build complete, cleaning up...");
    ContainerUtils.removeContainer(dockerClient, cs.getContainerId());
    lifecycle.tidyUp();

    return cs.getRc();
  }

  /**
   * Prep for a build.. this should pull the builder/runImage, and configure
   * the uid/gid to be used for the build.
   */
  private void prep() {

    // pull and inspect the builderImage to obtain builder metadata.
    ImageUtils.pullImages(dockerClient, pullTimeoutSeconds, builderImage);
    ImageInfo ii = ImageUtils.inspectImage(dockerClient, builderImage);

    // read the userid/groupid for the buildpack from it's env.
    for (String s : ii.env) {
      if (s.startsWith("CNB_USER_ID=")) {
        userId = Integer.valueOf(s.substring("CNB_USER_ID=".length()));
      }
      if (s.startsWith("CNB_GROUP_ID=")) {
        groupId = Integer.valueOf(s.substring("CNB_GROUP_ID=".length()));
      }
    }
    // override userid/groupid if cnb vars present within environment
    if(environment.containsKey("CNB_USER_ID")) { userId = Integer.valueOf(environment.get("CNB_USER_ID")); }
    if(environment.containsKey("CNB_GROUP_ID")) { userId = Integer.valueOf(environment.get("CNB_GROUP_ID")); }
    
    // obtain the buildpack metadata json.
    String metadataJson = ii.labels.get("io.buildpacks.builder.metadata");
    if(runImage==null)
      runImage = BuildpackMetadata.getRunImageFromMetadata(metadataJson);

    // TODO: read metadata from builderImage to confirm lifecycle version/platform
    // version compatibility.

    // pull the runImage, so it will be available for the build.
    ImageUtils.pullImages(dockerClient, pullTimeoutSeconds, runImage);

    // log out current config.
    log.info("Build configured with..");
    log.info("- build image : "+builderImage);
    log.info("- run image : "+runImage);
    log.info("- uid:"+userId+" gid:"+groupId);
    log.info("- dockerHost:"+dockerHost);
    log.info("- dockerSocket:"+dockerSocket);
  }

  public String getBuilderImage() {
    return builderImage;
  }

  public String getDockerSocket() {
    return dockerSocket;
  }

  public String getDockerNetwork() {
    return dockerNetwork;
  }

  public String getRunImage() {
    return runImage;
  }

  public String getFinalImage() {
    return finalImage;
  }

  public Integer getPullTimeoutSeconds() {
    return pullTimeoutSeconds;
  }

  public String getDockerHost() {
    return dockerHost;
  }

  public Boolean getUseDaemon() {
    return useDaemon;
  }

  public String getBuildCacheVolumeName() {
    return buildCacheVolumeName;
  }

  public String getBuildCacheVolumePath() {
    return buildCacheVolumePath;
  }

  public boolean getRemoveBuildCacheAfterBuild() {
    return removeBuildCacheAfterBuild;
  }

  public String getLaunchCacheVolumeName() {
    return launchCacheVolumeName;
  }

  public String getLaunchCacheVolumePath() {
    return launchCacheVolumePath;
  }

  public boolean getRemoveLaunchCacheAfterBuild() {
    return removeLaunchCacheAfterBuild;
  }

  public String getLogLevel() {
    return logLevel;
  }

  public boolean getUseTimestamps() {
    return useTimestamps;
  }

  public Map<String, String> getEnvironment() {
    return environment;
  }

  public void setEnvironment(Map<String, String> environment) {
    this.environment = environment;
  }

  public List<Content> getContent() {
    return content;
  }

  public void setContent(List<Content> content) {
    this.content = content;
  }

  public DockerClient getDockerClient() {
    return dockerClient;
  }

  public dev.snowdrop.buildpack.Logger getLogger() {
    return logger;
  }

  public int getExitCode() {
    return this.exitCode;
  }
  
}
