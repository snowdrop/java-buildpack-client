package dev.snowdrop.buildpack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.snowdrop.buildpack.BuildpackBuilder.LogReader;

public class LogRelay implements LogReader {
  private static final Logger log = LoggerFactory.getLogger(LogRelay.class);

  @Override
  public boolean stripAnsiColor() {
    return true;
  }

  @Override
  public void stdout(String message) {
    if (message.endsWith("\n")) {
      message = message.substring(0, message.length() - 1);
    }
    log.info(message);
  }

  @Override
  public void stderr(String message) {
    if (message.endsWith("\n")) {
      message = message.substring(0, message.length() - 1);
    }
    log.error(message);
  }

}
