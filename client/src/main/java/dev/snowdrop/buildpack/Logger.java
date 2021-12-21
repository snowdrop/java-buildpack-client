package dev.snowdrop.buildpack;

public interface Logger {

  void stdout(String message);
  void stderr(String message);

  default boolean isAnsiColorEnabled() {
    return false;
  }

  default String prepare(String message) {
    String result = new String(message);
    if (result.endsWith("\n")) {
      result = result.substring(0, result.length() - 1);
    }

    if (!isAnsiColorEnabled()) {
      result=result.replaceAll("[^m]+m", "");
    }
    return result;
  }
}
