package dev.snowdrop.buildpack.docker;

/**
 * Represents a named volume bound to a path in a container.
 */
public class VolumeBind {
  String volumeName;
  String mountPath;

  public VolumeBind(String volumeName, String mountPath) {
    this.volumeName = volumeName;
    this.mountPath = mountPath;
  }
}
