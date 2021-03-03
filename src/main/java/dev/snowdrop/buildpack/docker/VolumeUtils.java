package dev.snowdrop.buildpack.docker;

import java.io.File;
import java.io.IOException;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;

public class VolumeUtils {

  public static boolean createVolumeIfRequired(DockerClient dc, String volumeName) {
    if (!exists(dc, volumeName)) {
      return internalCreateVolume(dc, volumeName);
    } else {
      return true;
    }
  }

  public static boolean exists(DockerClient dc, String volumeName) {
    try {
      dc.inspectVolumeCmd(volumeName).exec();
      return true;
    } catch (NotFoundException e) {
      return false;
    }
  }

  public static void removeVolume(DockerClient dc, String volumeName) {
    dc.removeVolumeCmd(volumeName).exec();
  }

  public static boolean addContentToVolume(DockerClient dc, String volumeName, String pathInVolume, File content)
      throws IOException {
    return internalAddContentToVolume(dc, volumeName, ContainerEntry.fromFile("/volumecontent", content));
  }

  public static boolean addContentToVolume(DockerClient dc, String volumeName, String name, String content)
      throws IOException {
    return internalAddContentToVolume(dc, volumeName, ContainerEntry.fromString("/volumecontent/" + name, content));
  }

  private static boolean internalCreateVolume(DockerClient dc, String volumeName) {
    dc.createVolumeCmd().withName(volumeName).exec();
    return exists(dc, volumeName);
  }

  private static boolean internalAddContentToVolume(DockerClient dc, String volumeName, ContainerEntry... entries)
      throws IOException {
    // TODO: find better 'no-op' container to use?
    String dummyId = ContainerUtils.createContainer(dc, "tianon/true", new VolumeBind(volumeName, "/volumecontent"));

    ContainerUtils.addContentToContainer(dc, dummyId, entries);

    ContainerUtils.removeContainer(dc, dummyId);

    return true;
  }
}
