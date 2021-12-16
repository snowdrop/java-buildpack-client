
package dev.snowdrop.buildpack.docker;

import java.io.ByteArrayInputStream;

import io.sundr.builder.annotations.Buildable;

@Buildable(generateBuilderPackage=true, builderPackage="dev.snowdrop.buildpack.builder")
public class StringContent implements ContainerEntry {

  private final String path;
  private final String content;

  public StringContent(String path, String content) {
    this.path = path;
    this.content = content;
  }

  @Override
  public ContentSupplier getContentSupplier() {
    return () -> new ByteArrayInputStream(content.getBytes());
  }

  @Override
  public String getPath() {
    return path;
  }

  @Override
  public long getSize() {
    return content != null ? content.getBytes().length : 0;
  }

  public String getContent() {
    return this.content;
  }
}
