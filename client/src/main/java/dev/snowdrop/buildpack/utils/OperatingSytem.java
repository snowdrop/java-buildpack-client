package dev.snowdrop.buildpack.utils;

public enum OperatingSytem {

  WIN,
  LINUX,
  MAC,
  UNKNOWN;
  
  private static OperatingSytem os;

  public static OperatingSytem getOperationSystem() {
    if (os == null) {
      String osName = System.getProperty("os.name").toLowerCase();
      if (osName.contains("win")) {
        os = WIN;
      } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
        os = LINUX;
      } else if (osName.contains("mac")) {
        os = MAC;
      } else  { 
        os = UNKNOWN;
      }
    }
    return os;
  }
}
