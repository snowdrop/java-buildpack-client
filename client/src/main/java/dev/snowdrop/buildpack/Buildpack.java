package dev.snowdrop.buildpack;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.WaitContainerResultCallback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.snowdrop.buildpack.docker.ContainerEntry;
import dev.snowdrop.buildpack.docker.ContainerUtils;
import dev.snowdrop.buildpack.docker.Content;
import dev.snowdrop.buildpack.docker.DockerClientUtils;
import dev.snowdrop.buildpack.docker.ImageUtils;
import dev.snowdrop.buildpack.docker.StringContent;
import dev.snowdrop.buildpack.docker.ImageUtils.ImageInfo;
import dev.snowdrop.buildpack.docker.VolumeBind;
import dev.snowdrop.buildpack.docker.VolumeUtils;
import io.sundr.builder.annotations.Buildable;

@Buildable(generateBuilderPackage=true, builderPackage="dev.snowdrop.buildpack.builder")
public class Buildpack {

  public static BuildpackBuilder builder() {
    return new BuildpackBuilder();
  }

  private static final Logger log = LoggerFactory.getLogger(Buildpack.class);

  //paths we use for mountpoints within build container.
  private final String BUILD_VOL_PATH = "/bld";
  private final String LAUNCH_VOL_PATH = "/launch";
  private final String APP_VOL_PATH = "/app";
  private final String OUTPUT_VOL_PATH = "/out";
  private final String PLATFORM_VOL_PATH = "/platform";



  private static final String DEFAULT_BUILD_IMAGE = "paketobuildpacks/builder:base";
  private static final Integer DEFAULT_PULL_TIMEOUT = 60;
  private static final String DEFAULT_LOG_LEVEL = "debug";

  // defaults for images
  private final String buildImage;
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

  private List<ContainerEntry> containerEntries = new LinkedList<>();

  public Buildpack(String buildImage, String runImage, String finalImage, Integer pullTimeoutSeconds, String dockerHost,
      boolean useDaemon, String buildCacheVolumeName, boolean removeBuildCacheAfterBuild,
      String launchCacheVolumeName, boolean removeLaunchCacheAfterBuild, String logLevel, boolean useTimestamps, Map<String, String> environment, List<Content> content, DockerClient dockerClient, dev.snowdrop.buildpack.Logger logger) {
    this.buildImage = buildImage != null ? buildImage : DEFAULT_BUILD_IMAGE;
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
    this.containerEntries = content != null ? content.stream().flatMap(c -> c.getContainerEntries().stream()).collect(Collectors.toList()) : Collections.emptyList();
    this.dockerClient = DockerClientUtils.getDockerClient(dockerHost);
    this.logger = logger != null ? logger : new SystemLogger();
    build(this.logger);
 }

  private int build(dev.snowdrop.buildpack.Logger logger) {
    log.info("Buildpack build invoked, preparing environment...");
    
    prep();

    String buildCacheVolume = buildCacheVolumeName == null ? "buildpack-build-" + randomString(10) : buildCacheVolumeName;
    String launchCacheVolume = launchCacheVolumeName == null ? "buildpack-launch-" + randomString(10) : launchCacheVolumeName;

    String applicationVolume = "buildpack-app-" + randomString(10);
    String outputVolume = "buildpack-output-" + randomString(10);
    String platformVolume = "buildpack-platform-" + randomString(10);

    // create all the volumes we plan to use =)
    VolumeUtils.createVolumeIfRequired(dockerClient, buildCacheVolume);
    VolumeUtils.createVolumeIfRequired(dockerClient, launchCacheVolume);
    VolumeUtils.createVolumeIfRequired(dockerClient, applicationVolume);
    VolumeUtils.createVolumeIfRequired(dockerClient, outputVolume);
    VolumeUtils.createVolumeIfRequired(dockerClient, platformVolume);
    log.info("- build volumes created");
    
    // configure our call to 'creator' which will do all the work.
    String[] xargs = { "bash", "-c", "ls -alR "+PLATFORM_VOL_PATH };

    String[] args = { "/cnb/lifecycle/creator", 
                      "-uid", "" + userId, 
                      "-gid", "" + groupId, 
                      "-cache-dir", BUILD_VOL_PATH,
                      "-app", APP_VOL_PATH + "/content", 
                      "-layers", OUTPUT_VOL_PATH, 
                      "-platform", PLATFORM_VOL_PATH, 
                      "-run-image", runImage, 
                      "-launch-cache", LAUNCH_VOL_PATH, 
                      "-daemon", // TODO: non daemon support.
                      "-log-level", this.logLevel, 
                      "-skip-restore", finalImage };

    // TODO: read metadata from buildImage to confirm lifecycle version/platform
    // version compatibility.

    // TODO: add labels for container for creator etc (as per spec)

    // docker socket?
    String dockerSocket = "/var/run/docker.sock";
    if (dockerHost != null && dockerHost.startsWith("unix://")) {
      dockerSocket = dockerHost.substring("unix://".length());
    }

    // create a container using buildImage that will invoke the creator process
    String id = ContainerUtils.createContainer(dockerClient, buildImage, Arrays.asList(args),
        new VolumeBind(buildCacheVolume, BUILD_VOL_PATH), new VolumeBind(launchCacheVolume, LAUNCH_VOL_PATH),
        new VolumeBind(applicationVolume, APP_VOL_PATH), new VolumeBind(dockerSocket, "/var/run/docker.sock"),
        new VolumeBind(outputVolume, OUTPUT_VOL_PATH));

    log.info("- mounted " + buildCacheVolume + " at " + BUILD_VOL_PATH);
    log.info("- mounted " + launchCacheVolume + " at " + LAUNCH_VOL_PATH);
    log.info("- mounted " + applicationVolume + " at " + APP_VOL_PATH);
    log.info("- mounted " + platformVolume + " at " + PLATFORM_VOL_PATH);
    log.info("- mounted " + dockerSocket + " at " + "/var/run/docker.sock");
    log.info("- mounted " + outputVolume + " at " + OUTPUT_VOL_PATH);
    log.info("- build container id " + id);

    // add the application to the container. Note we are placing it at /app/content,
    // because the /app mountpoint is mounted such that the user has no perms to create 
    // new content there, but subdirs are ok.
    List<ContainerEntry> appEntries = content
      .stream()
      .flatMap(c -> c.getContainerEntries().stream())
      .collect(Collectors.toList());

    ContainerUtils.addContentToContainer(dockerClient, id, APP_VOL_PATH + "/content", userId, groupId, appEntries);
    log.info("- uploaded archive to container at " + APP_VOL_PATH + "/content");
    
    //add the environment entries.
    List<ContainerEntry> envEntries = environment.entrySet()
      .stream()
      .flatMap(e -> new StringContent(e.getKey(), e.getValue()).getContainerEntries().stream())
      .collect(Collectors.toList());

    ContainerUtils.addContentToContainer(dockerClient, id, PLATFORM_VOL_PATH + "/env", userId, groupId, envEntries);
    log.info("- uploaded env to container at " + PLATFORM_VOL_PATH + "/env");  

    // launch the container!
    log.info("- launching build container");
    dockerClient.startContainerCmd(id).exec();

    log.info("- attaching log relay");
    // grab the logs to stdout.
    dockerClient.logContainerCmd(id)
      .withFollowStream(true)
      .withStdOut(true)
      .withStdErr(true)
      .withTimestamps(this.useTimestamps)
      .exec(new ContainerLogReader(logger));

    // wait for the container to complete, and retrieve the exit code.
    int rc = dockerClient.waitContainerCmd(id).exec(new WaitContainerResultCallback()).awaitStatusCode();
    log.info("Buildpack build complete, with exit code " + rc);

    // tidy up. remove container.
    ContainerUtils.removeContainer(dockerClient, id);

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

    return rc;
  }
  
  private void prep() {

    ImageUtils.pullImages(dockerClient, pullTimeoutSeconds, buildImage);
    ImageInfo ii = ImageUtils.inspectImage(dockerClient, buildImage);

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
    ObjectMapper om = new ObjectMapper();
    om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    try {
      JsonNode root = om.readTree(metadataJson);
      // read the buildpacks recommended runImage
      String ri = getValue(root, "stack/runImage/image");
      // if caller didn't set runImage, use one from buildPack.
      if (runImage == null) {
        if (ri == null) {
          throw new Exception("No runImage specified, and builderImage is missing metadata declaration");
        } else {
          if (ri.startsWith("index.docker.io/")) {
            ri = ri.substring("index.docker.io/".length());
            ri = "docker.io/" + ri;
          }
          runImage = ri;
        }
      }
    } catch (Exception e) {
      throw BuildpackException.launderThrowable(e);
    }

    // pull the runImage.
    ImageUtils.pullImages(dockerClient, pullTimeoutSeconds, runImage);
    
    log.info("Build configured with..");
    log.info("- build image : "+buildImage);
    log.info("- run image : "+runImage);
  }

  private String getValue(JsonNode root, String path) {
    String[] parts = path.split("/");
    JsonNode next = root.get(parts[0]);
    if (next != null && parts.length > 1) {
      return getValue(next, path.substring(path.indexOf("/") + 1));
    }
    if (next == null) {
      return null;
    }
    return next.asText();
  }

  // util method for random suffix.
  private String randomString(int length) {
    return (new Random()).ints('a', 'z' + 1).limit(length)
        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
  }

  public String getBuildImage() {
    return buildImage;
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
  
}
