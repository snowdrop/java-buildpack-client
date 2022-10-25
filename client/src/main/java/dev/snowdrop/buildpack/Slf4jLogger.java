package dev.snowdrop.buildpack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.sundr.builder.annotations.Buildable;

@Buildable(generateBuilderPackage=true, builderPackage="dev.snowdrop.buildpack.builder")
public class Slf4jLogger implements dev.snowdrop.buildpack.Logger {

  private final Logger log;
  private final String name;

  public Slf4jLogger(String name) {
    this.name = name;
    this.log = LoggerFactory.getLogger(name);
  }

  public Slf4jLogger(Class<?> c) {
    this.name = c.getCanonicalName();
    this.log = LoggerFactory.getLogger(c);
  }

  @Override
  public void stdout(String message) {
    log.info(prepare(message));
  }

  @Override
  public void stderr(String message) {
    log.error(prepare(message));
  }

  public String getName() {
    return name;
  }
}
