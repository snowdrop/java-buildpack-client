package dev.snowdrop.buildpack;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import com.github.dockerjava.api.DockerClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.snowdrop.buildpack.docker.ContainerEntry;
import dev.snowdrop.buildpack.docker.ContainerUtils;
import dev.snowdrop.buildpack.docker.Content;
import dev.snowdrop.buildpack.docker.DockerClientUtils;
import dev.snowdrop.buildpack.docker.ImageUtils;
import dev.snowdrop.buildpack.docker.StringContent;
import dev.snowdrop.buildpack.docker.ImageUtils.ImageInfo;
import dev.snowdrop.buildpack.phases.ContainerStatus;
import dev.snowdrop.buildpack.phases.LifecyclePhase;
import dev.snowdrop.buildpack.phases.LifecyclePhaseFactory;
import dev.snowdrop.buildpack.utils.BuildpackMetadata;
import dev.snowdrop.buildpack.docker.VolumeUtils;
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

  //names of the volumes during runtime.
  private final String buildCacheVolume;
  private final String launchCacheVolume;
  private final String applicationVolume;
  private final String outputVolume;
  private final String platformVolume;

  // defaults for images
  private final String builderImage;
  private final String finalImage;
  private String runImage;

  private final Integer pullTimeoutSeconds;

  private final String dockerHost;

  private final boolean useDaemon;
  private final String buildCacheVolumeName;
  private final boolean removeBuildCacheAfterBuild;
  private final String launchCacheVolumeName;
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

  public Buildpack(String builderImage, String runImage, String finalImage, Integer pullTimeoutSeconds, String dockerHost,
      boolean useDaemon, String buildCacheVolumeName, boolean removeBuildCacheAfterBuild,
      String launchCacheVolumeName, boolean removeLaunchCacheAfterBuild, String logLevel, boolean useTimestamps, Map<String, String> environment, List<Content> content, DockerClient dockerClient, dev.snowdrop.buildpack.Logger logger) {

    this.builderImage = builderImage != null ? builderImage : DEFAULT_BUILD_IMAGE;
    this.runImage = runImage;
    this.finalImage = finalImage;
    this.pullTimeoutSeconds = pullTimeoutSeconds != null ? pullTimeoutSeconds : DEFAULT_PULL_TIMEOUT;
    this.dockerHost = dockerHost;
    this.useDaemon = useDaemon;
    this.buildCacheVolumeName = buildCacheVolumeName;
    this.removeBuildCacheAfterBuild = removeBuildCacheAfterBuild;
    this.launchCacheVolumeName = launchCacheVolumeName;
    this.removeLaunchCacheAfterBuild = removeLaunchCacheAfterBuild;
    this.logLevel = logLevel != null ? logLevel : DEFAULT_LOG_LEVEL;
    this.useTimestamps = useTimestamps;
    this.environment = environment != null ? environment : new HashMap<>();
    this.content = content;
    this.dockerClient = DockerClientUtils.getDockerClient(dockerHost);
    this.logger = logger != null ? logger : new SystemLogger();

    this.buildCacheVolume = buildCacheVolumeName == null ? "buildpack-build-" + randomString(10) : buildCacheVolumeName;
    this.launchCacheVolume = launchCacheVolumeName == null ? "buildpack-launch-" + randomString(10) : launchCacheVolumeName;
    this.applicationVolume = "buildpack-app-" + randomString(10);
    this.outputVolume = "buildpack-output-" + randomString(10);
    this.platformVolume = "buildpack-platform-" + randomString(10);

    this.exitCode = build(logger);
  }

  private int build(dev.snowdrop.buildpack.Logger logger) {

    log.info("Buildpack build invoked, preparing environment...");
    prep();

    // We still only support docker daemon execution, and if 
    // dockerHost is configured, then test if it is a unix:// path
    // and reset dockerSocket path appropriately.
    String dockerSocket = "/var/run/docker.sock";
    if (dockerHost != null && dockerHost.startsWith("unix://")) {
      dockerSocket = dockerHost.substring("unix://".length());
    }

    // declare our volume mappings
    Map<String,String> volMappings = new HashMap<>();
    volMappings.put(LifecyclePhaseFactory.BUILD_VOL_PATH, buildCacheVolume);
    volMappings.put(LifecyclePhaseFactory.LAUNCH_VOL_PATH,launchCacheVolume);
    volMappings.put(LifecyclePhaseFactory.APP_VOL_PATH,applicationVolume);
    volMappings.put(LifecyclePhaseFactory.OUTPUT_VOL_PATH,outputVolume);
    volMappings.put(LifecyclePhaseFactory.PLATFORM_VOL_PATH, platformVolume);
    volMappings.put(LifecyclePhaseFactory.DOCKER_SOCKET_PATH, dockerSocket);

    LifecyclePhaseFactory lifecycle = new LifecyclePhaseFactory(dockerClient, dockerSocket, builderImage, userId, groupId, logLevel, runImage, finalImage, volMappings);

    //create and run the creator phase
    LifecyclePhase creator = lifecycle.getCreator();
    ContainerStatus cs = creator.runPhase(logger, useTimestamps);
   
    log.info("Buildpack build complete, cleaning up...");
    tidyUp(cs.getContainerId());
    return cs.getRc();
  }

  
  private void createVolumes(){
    // create all the volumes we plan to use =)
    VolumeUtils.createVolumeIfRequired(dockerClient, buildCacheVolume);
    VolumeUtils.createVolumeIfRequired(dockerClient, launchCacheVolume);
    VolumeUtils.createVolumeIfRequired(dockerClient, applicationVolume);
    VolumeUtils.createVolumeIfRequired(dockerClient, outputVolume);
    VolumeUtils.createVolumeIfRequired(dockerClient, platformVolume);

    log.info("- build volumes created");
  }

  private void removeVolumes(){
    // remove volumes
    // (note when/if we persist the cache between builds, we'll be more selective
    // here over what we remove)
    if (removeBuildCacheAfterBuild || buildCacheVolumeName == null) {
      VolumeUtils.removeVolume(dockerClient, buildCacheVolume);
    }
    if (removeLaunchCacheAfterBuild || launchCacheVolumeName == null) {
      VolumeUtils.removeVolume(dockerClient, launchCacheVolume);
    }
    VolumeUtils.removeVolume(dockerClient, applicationVolume);
    VolumeUtils.removeVolume(dockerClient, outputVolume);
    VolumeUtils.removeVolume(dockerClient, platformVolume);

    log.info("- build volumes tidied up");
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
    
    // pull the buildpack metadata json.
    String metadataJson = ii.labels.get("io.buildpacks.builder.metadata");
    runImage = BuildpackMetadata.getRunImageFromMetadata(metadataJson, runImage);

    // pull the runImage, so it will be available for the build.
    ImageUtils.pullImages(dockerClient, pullTimeoutSeconds, runImage);

    // create the volumes.
    createVolumes();

    // add the application to the volume. Note we are placing it at /content,
    // because the volume mountpoint is mounted such that the user has no perms to create 
    // new content there, but subdirs are ok.
    List<ContainerEntry> appEntries = content
      .stream()
      .flatMap(c -> c.getContainerEntries().stream())
      .collect(Collectors.toList());
    VolumeUtils.addContentToVolume(dockerClient, applicationVolume, "/content", userId, groupId, appEntries);

    //add the environment entries to the platform volume.
    List<ContainerEntry> envEntries = environment.entrySet()
      .stream()
      .flatMap(e -> new StringContent(e.getKey(), e.getValue()).getContainerEntries().stream())
      .collect(Collectors.toList());
    VolumeUtils.addContentToVolume(dockerClient, platformVolume, "/env", userId, groupId, envEntries);    
    
    // log out current config.
    log.info("Build configured with..");
    log.info("- build image : "+builderImage);
    log.info("- run image : "+runImage);
    log.info("- uid:"+userId+" gid:"+groupId);
  }

  private void tidyUp(String containerIdToClean) {
    // tidy up. remove container.
    ContainerUtils.removeContainer(dockerClient, containerIdToClean);
    log.info("- build container tidied up");

    removeVolumes();
  }

  // util method for random suffix.
  private String randomString(int length) {
    return (new Random()).ints('a', 'z' + 1).limit(length)
        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
  }

  public String getBuilderImage() {
    return builderImage;
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

  public boolean getUseDaemon() {
    return useDaemon;
  }

  public String getBuildCacheVolumeName() {
    return buildCacheVolumeName;
  }

  public boolean getRemoveBuildCacheAfterBuild() {
    return removeBuildCacheAfterBuild;
  }

  public String getLaunchCacheVolumeName() {
    return launchCacheVolumeName;
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
