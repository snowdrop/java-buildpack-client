package dev.snowdrop.buildpack;

import dev.snowdrop.buildpack.BuildpackBuilder.LogReader;

public class SystemRelay implements LogReader {

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

}
