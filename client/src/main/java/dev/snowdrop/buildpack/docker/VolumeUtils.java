package dev.snowdrop.buildpack.docker;

import java.io.File;
import java.util.List;
import org.slf4j.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;

public class VolumeUtils {

  private static final Logger log = LoggerFactory.getLogger(VolumeUtils.class);

  //It is critical that we use a mountpoint that exists and is not owned by root, otherwise the volume can gain
  //'sticky' root ownership that cannot be undone. As such, we use the /workspace dir, as we know the ephemeral builder
  //will have this present as owned by build uid/gid.
  final static String mountPrefix = "/workspace";

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

  public static boolean addContentToVolume(DockerClient dc, String volumeName, String useImage, String name, Integer mode, String content) {
    return internalAddContentToVolume(dc, volumeName, useImage, mountPrefix, 0,0, new StringContent(name, mode, content).getContainerEntries());
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
    try{
      log.debug("Adding content to volume "+volumeName+" under prefix "+prefix+" using image "+useImage+" with volume bound at "+mountPrefix+" temp container id "+dummyId);
      ContainerUtils.addContentToContainer(dc, dummyId, prefix, uid, gid, entries);
      return true;
    }finally{
      if(dummyId!=null){
        ContainerUtils.removeContainer(dc, dummyId);
      }
    }
  }
}
