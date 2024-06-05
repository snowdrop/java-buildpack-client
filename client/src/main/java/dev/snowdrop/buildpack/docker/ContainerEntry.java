package dev.snowdrop.buildpack.docker;

import java.io.InputStream;

/**
 * Abstraction representing an entry in a container. Allows for entries to be
 * added from sources other than File/Directory (eg, String as a test content,
 * or programmatic supply via future i/f)
 */
public interface ContainerEntry {

  
  public String getPath();

  public long getSize();

  public Integer getMode();

  public DataSupplier getDataSupplier();

  @FunctionalInterface
  public interface DataSupplier {
    InputStream getData();
  }
}
