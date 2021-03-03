package dev.snowdrop.buildpack;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;

import dev.snowdrop.buildpack.docker.ContainerEntry;
import dev.snowdrop.buildpack.docker.ContainerEntry.ContentSupplier;
import dev.snowdrop.buildpack.docker.ContainerUtils;
import dev.snowdrop.buildpack.docker.DockerClientUtils;
import dev.snowdrop.buildpack.docker.ImageUtils;
import dev.snowdrop.buildpack.docker.ImageUtils.ImageInfo;
import dev.snowdrop.buildpack.docker.VolumeBind;
import dev.snowdrop.buildpack.docker.VolumeUtils;

public class BuildPackBuilderImpl implements BuildPackBuilder {
  private final String PLATFORM_API_VERSION = "0.5";

  private final String BUILD_VOL_PATH = "/bld";
  private final String LAUNCH_VOL_PATH = "/launch";
  private final String APP_VOL_PATH = "/app";
  private final String OUTPUT_VOL_PATH = "/out";
  private final String ENV_VOL_PATH = "/env";

  private String buildImage = "paketobuildpacks/builder:base";
  private String runImage = null;
  private String finalImage = null;

  private int pullTimeoutSeconds = 60;

  private String dockerHost = null;
  private DockerClient dc;

  private boolean useDaemon = true;
  private String buildCacheVolumeName = null;
  private boolean removeBuildCacheAfterBuild = false;
  private String launchCacheVolumeName = null;
  private boolean removeLaunchCacheAfterBuild = false;

  private String logLevel = "debug";
  private boolean useTimestamps = true;

  private int userId = 0;
  private int groupId = 0;

  Map<String, String> environment = new HashMap<>();
  LinkedList<ContainerEntry> applicationContent = new LinkedList<>();

  public BuildPackBuilderImpl() {
    dc = DockerClientUtils.getDockerClient();
  }

  public BuildPackBuilder fromBuilder(BuildPackBuilder builder) {
    if (builder instanceof BuildPackBuilderImpl) {
      BuildPackBuilderImpl bpi = (BuildPackBuilderImpl) builder;
      this.buildImage = bpi.buildImage;
      this.runImage = bpi.runImage;
      this.finalImage = bpi.finalImage;
      this.dockerHost = bpi.dockerHost;
      this.dc = bpi.dc;
      this.environment.putAll(bpi.environment);
      bpi.applicationContent.forEach(ce -> this.applicationContent.addLast(ce));
      this.useDaemon = bpi.useDaemon;
      this.buildCacheVolumeName = bpi.buildCacheVolumeName;
      this.launchCacheVolumeName = bpi.launchCacheVolumeName;
      this.removeBuildCacheAfterBuild = bpi.removeBuildCacheAfterBuild;
      this.removeLaunchCacheAfterBuild = bpi.removeLaunchCacheAfterBuild;
      this.logLevel = bpi.logLevel;
      this.useTimestamps = bpi.useTimestamps;
      this.pullTimeoutSeconds = bpi.pullTimeoutSeconds;
    }
    return this;
  }

  public BuildPackBuilder withRunImage(String image) {
    this.runImage = image;
    return this;
  }

  public BuildPackBuilder withBuildImage(String image) {
    this.buildImage = image;
    return this;
  }

  public BuildPackBuilder withEnv(String key, String value) {
    this.environment.put(key, value);
    return this;
  }

  public BuildPackBuilder withEnv(Map<String, String> environment) {
    this.environment.putAll(environment);
    return this;
  }

  public BuildPackBuilder withDockerHost(String dockerHost) {
    this.dockerHost = dockerHost;
    this.dc = DockerClientUtils.getDockerClient(dockerHost);
    return this;
  }

  public BuildPackBuilder withContent(String filepath, String filecontent) {
    try {
      this.applicationContent.addLast(ContainerEntry.fromString(filepath, filecontent));
    } catch (Exception e) {
      // not-possible with string.
    }
    return this;
  }

  public BuildPackBuilder withContent(String filepath, long length, ContentSupplier content) throws Exception {
    this.applicationContent.addLast(ContainerEntry.fromStream(filepath, length, content));
    return this;
  }

  public BuildPackBuilder withContent(File content) throws Exception {
    return this.withContent("", content);
  }

  public BuildPackBuilder withContent(String prefix, File content) throws Exception {
    ContainerEntry[] entries = ContainerEntry.fromFile(prefix, content);
    if (entries != null) {
      for (ContainerEntry ce : entries) {
        this.applicationContent.addLast(ce);
      }
    }
    return this;
  }

  public BuildPackBuilder withContent(ContainerEntry... entries) throws Exception {
    for (ContainerEntry ce : entries) {
      this.applicationContent.addLast(ce);
    }
    return this;
  }

  public BuildPackBuilder useDockerDaemon(boolean useDaemon) {
    this.useDaemon = useDaemon;
    return this;
  }

  public BuildPackBuilder withBuildCache(String cacheVolume) {
    this.buildCacheVolumeName = cacheVolume;
    return this;
  }

  public BuildPackBuilder removeBuildCacheAfterBuild(boolean remove) {
    this.removeBuildCacheAfterBuild = remove;
    return this;
  }

  public BuildPackBuilder withLaunchCache(String cacheVolume) {
    this.launchCacheVolumeName = cacheVolume;
    return this;
  }

  public BuildPackBuilder removeLaunchCacheAfterBuild(boolean remove) {
    this.removeLaunchCacheAfterBuild = remove;
    return this;
  }

  public BuildPackBuilder withPullTimeout(int seconds) {
    this.pullTimeoutSeconds = seconds;
    return this;
  }

  public BuildPackBuilder withLogLevel(String logLevel) {
    this.logLevel = logLevel;
    return this;
  }

  public BuildPackBuilder requestBuildTimestamps(boolean timestampsEnabled) {
    this.useTimestamps = timestampsEnabled;
    return this;
  }

  public BuildPackBuilder withFinalImage(String image) {
    this.finalImage = image;
    return this;
  }

  public int build() throws Exception {
    BuildPackBuilder.LogReader system = new BuildPackBuilder.LogReader() {
      @Override
      public boolean stripAnsiColor() {
        return true;
      }

      @Override
      public void stdout(String message) {
        if (message.endsWith("\n")) {
          message = message.substring(0, message.length() - 1);
        }
        System.out.println(message);
      }

      @Override
      public void stderr(String message) {
        if (message.endsWith("\n")) {
          message = message.substring(0, message.length() - 1);
        }
        System.err.println(message);
      }
    };
    return build(system);
  }

  public int build(LogReader logger) throws Exception {
    prep();

    String buildCacheVolume = buildCacheVolumeName == null ? "build-" + randomString(10) : buildCacheVolumeName;
    String launchCacheVolume = launchCacheVolumeName == null ? "launch-" + randomString(10) : launchCacheVolumeName;

    String applicationVolume = "app-" + randomString(10);
    String outputVolume = "output-" + randomString(10);
    String envVolume = "env-" + randomString(10);

    // create all the volumes we plan to use =)
    VolumeUtils.createVolumeIfRequired(dc, buildCacheVolume);
    VolumeUtils.createVolumeIfRequired(dc, launchCacheVolume);
    VolumeUtils.createVolumeIfRequired(dc, applicationVolume);
    VolumeUtils.createVolumeIfRequired(dc, outputVolume);
    VolumeUtils.createVolumeIfRequired(dc, envVolume);

    // configure our call to 'creator' which will do all the work.
    String[] xargs = { "bash", "-c", "ls -alR /app" };
    String[] args = { "/cnb/lifecycle/creator", "-uid", "" + userId, "-gid", "" + groupId, "-cache-dir", BUILD_VOL_PATH,
        "-app", APP_VOL_PATH + "/content", "-layers", OUTPUT_VOL_PATH, "-platform", ENV_VOL_PATH, "-run-image",
        runImage, "-launch-cache", LAUNCH_VOL_PATH, "-daemon", // TODO: non daemon
        // support.
        "-log-level", this.logLevel, "-skip-restore", finalImage };

    // TODO: read metadata from buildImage to confirm lifecycle version/platform
    // version compatibility.

    // TODO: add labels for container for creator etc (as per spec)

    // docker socket?
    String dockerSocket = "/var/run/docker.sock";
    if (dockerHost != null && dockerHost.startsWith("unix://")) {
      dockerSocket = dockerHost.substring("unix://".length());
    }

    // create a container using buildImage that will invoke the creator process
    String id = ContainerUtils.createContainer(dc, buildImage, Arrays.asList(args),
        new VolumeBind(buildCacheVolume, BUILD_VOL_PATH), new VolumeBind(launchCacheVolume, LAUNCH_VOL_PATH),
        new VolumeBind(applicationVolume, APP_VOL_PATH), new VolumeBind(dockerSocket, "/var/run/docker.sock"),
        new VolumeBind(outputVolume, OUTPUT_VOL_PATH));

    // info prints.. remove later =)
    System.out.println("mounted " + buildCacheVolume + " at " + BUILD_VOL_PATH);
    System.out.println("mounted " + launchCacheVolume + " at " + LAUNCH_VOL_PATH);
    System.out.println("mounted " + applicationVolume + " at " + APP_VOL_PATH);
    System.out.println("mounted " + dockerSocket + " at " + "/var/run/docker.sock");
    System.out.println("mounted " + outputVolume + " at " + OUTPUT_VOL_PATH);
    System.out.println("container id " + id);

    // TODO: add environment volume content from caller (add to method args)
    // VolumeUtils.addContentToVolume(dc, envVolume, "env/CNB_SKIP_LAYERS", "true");

    // add the application to the container. Note we are placing it at /app/content,
    // because
    // the /app mountpoint is mounted such that the user has no perms to create new
    // content there,
    // but subdirs are ok.
    ContainerEntry[] entries = applicationContent.toArray(new ContainerEntry[0]);
    ContainerUtils.addContentToContainer(dc, id, APP_VOL_PATH + "/content", userId, groupId, entries);
    System.out.println("uploaded archive to container at " + APP_VOL_PATH + "/content");

    // launch the container!
    dc.startContainerCmd(id).exec();

    // grab the logs to stdout.
    dc.logContainerCmd(id).withFollowStream(true).withStdOut(true).withStdErr(true).withTimestamps(this.useTimestamps)
        .exec(new ContainerLogReader(logger));

    // wait for the container to complete, and retrieve the exit code.
    int rc = dc.waitContainerCmd(id).exec(new WaitContainerResultCallback()).awaitStatusCode();
    System.out.println("Build exited with code " + rc);

    // tidy up. remove container.
    ContainerUtils.removeContainer(dc, id);

    // remove volumes
    // (note when/if we persist the cache between builds, we'll be more selective
    // here over what we remove)
    if (removeBuildCacheAfterBuild || buildCacheVolumeName == null) {
      VolumeUtils.removeVolume(dc, buildCacheVolume);
    }
    if (removeLaunchCacheAfterBuild || launchCacheVolumeName == null) {
      VolumeUtils.removeVolume(dc, launchCacheVolume);
    }
    VolumeUtils.removeVolume(dc, applicationVolume);
    VolumeUtils.removeVolume(dc, outputVolume);
    VolumeUtils.removeVolume(dc, envVolume);

    return rc;
  }

  private void prep() throws Exception {

    ImageUtils.pullImages(dc, pullTimeoutSeconds, buildImage);
    ImageInfo ii = ImageUtils.inspectImage(dc, buildImage);

    // read the userid/groupid for the buildpack from it's env.
    for (String s : ii.env) {
      if (s.startsWith("CNB_USER_ID=")) {
        userId = Integer.valueOf(s.substring("CNB_USER_ID=".length()));
      }
      if (s.startsWith("CNB_GROUP_ID=")) {
        groupId = Integer.valueOf(s.substring("CNB_GROUP_ID=".length()));
      }
    }

    // pull the buildpack metadata json.
    String metadataJson = ii.labels.get("io.buildpacks.builder.metadata");
    ObjectMapper om = new ObjectMapper();
    om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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
        System.out.println("Got builder specified ri " + ri);
      }
    }

    // pull the runImage.
    ImageUtils.pullImages(dc, pullTimeoutSeconds, runImage);
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

  // daft little adapter class to read log output from docker-java and spew it to
  // the logger
  // we strip ansi color escape sequences for clarity.
  private static class ContainerLogReader extends ResultCallback.Adapter<Frame> {
    private final BuildPackBuilder.LogReader logger;
    private boolean stripColor;

    public ContainerLogReader(BuildPackBuilder.LogReader logger) {
      this.logger = logger;
      this.stripColor = this.logger.stripAnsiColor();
    }

    @Override
    public void onNext(Frame object) {
      if (StreamType.STDOUT == object.getStreamType() || StreamType.STDERR == object.getStreamType()) {

        String payload = new String(object.getPayload(), UTF_8);
        if (stripColor) {
          payload = payload.replaceAll("[^m]+m", "");
        }
        if (StreamType.STDOUT == object.getStreamType()) {
          logger.stdout(payload);
        } else if (StreamType.STDERR == object.getStreamType()) {
          logger.stderr(payload);
        }

      }
    }
  }

  // util method for random suffix.
  private String randomString(int length) {
    return (new Random()).ints('a', 'z' + 1).limit(length)
        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
  }
}
