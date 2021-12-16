
package dev.snowdrop.buildpack.docker;

import io.sundr.builder.annotations.Buildable;

@Buildable(generateBuilderPackage=true, builderPackage="dev.snowdrop.buildpack.builder")
public class StreamContent implements ContainerEntry {

  private final String path;
  private final Long size;
  private final ContentSupplier contentSupplier;

  public StreamContent(String path, Long size, ContentSupplier contentSupplier) {
    this.path = path;
    this.size = size;
    this.contentSupplier = contentSupplier;
  }

  @Override
  public ContentSupplier getContentSupplier() {
    return contentSupplier;
  }

  @Override
  public String getPath() {
    return path;
  }

  @Override
  public long getSize() {
    return size;
  }


}
