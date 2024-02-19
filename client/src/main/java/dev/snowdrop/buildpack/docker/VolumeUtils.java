package dev.snowdrop.buildpack.docker;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;

public class VolumeUtils {

  final static String mountPrefix = "/volumecontent";

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

  public static boolean addContentToVolume(DockerClient dc, String volumeName, String useImage, String pathInVolume, File content) {
    return internalAddContentToVolume(dc, volumeName, useImage, mountPrefix, 0,0, new FileContent(content).getContainerEntries());
  }

  public static boolean addContentToVolume(DockerClient dc, String volumeName, String useImage, String name, String content) {
    return internalAddContentToVolume(dc, volumeName, useImage, mountPrefix, 0,0, new StringContent(name, content).getContainerEntries());
  }

  public static boolean addContentToVolume(DockerClient dc, String volumeName, String useImage, String prefix, int uid, int gid, List<ContainerEntry> entries) {
    if(!prefix.startsWith("/")) prefix = "/"+prefix;
    return internalAddContentToVolume(dc, volumeName, useImage, mountPrefix+prefix, uid, gid, entries);
  }

  private static boolean internalCreateVolume(DockerClient dc, String volumeName) {
    dc.createVolumeCmd().withName(volumeName).exec();
    return exists(dc, volumeName);
  }

  private static boolean internalAddContentToVolume(DockerClient dc, String volumeName, String useImage, String prefix, int uid, int gid, List<ContainerEntry> entries) {
    return internalAddContentToVolume(dc, volumeName, useImage, prefix, uid, gid, entries.toArray(new ContainerEntry[entries.size()]));
  }

  private static boolean internalAddContentToVolume(DockerClient dc, String volumeName, String useImage, String prefix, int uid, int gid, ContainerEntry... entries) {
    
    List<String> command = Stream.of("").collect(Collectors.toList());
    String dummyId = ContainerUtils.createContainer(dc, useImage, command, new VolumeBind(volumeName, mountPrefix));
    ContainerUtils.addContentToContainer(dc, dummyId, prefix, uid, gid, entries);
    ContainerUtils.removeContainer(dc, dummyId);
    return true;
  }
}
