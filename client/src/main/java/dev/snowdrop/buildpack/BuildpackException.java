package dev.snowdrop.buildpack;

public class BuildpackException extends RuntimeException {

  public BuildpackException() {
  }

  public BuildpackException(Throwable cause) {
    super(cause);
  }

  public BuildpackException(String message, Throwable cause) {
    super(message, cause);
  }

  public static RuntimeException launderThrowable(Throwable cause) {
    return launderThrowable(cause.getMessage(), cause);
  }

  public static RuntimeException launderThrowable(String message, Throwable cause) {
    if (cause instanceof RuntimeException) {
      return ((RuntimeException) cause);
    } else if (cause instanceof Error) {
      throw ((Error) cause);
    } else if (cause instanceof InterruptedException) {
      Thread.currentThread().interrupt();
    }
    return new BuildpackException(message, cause);
  }
}
