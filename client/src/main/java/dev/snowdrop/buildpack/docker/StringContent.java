
package dev.snowdrop.buildpack.docker;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;

import io.sundr.builder.annotations.Buildable;

@Buildable(generateBuilderPackage = true, builderPackage = "dev.snowdrop.buildpack.builder")
public class StringContent implements Content {

  private final String path;
  private final String content;

  public StringContent(String path, String content) {
    this.path = path;
    this.content = content;
  }

  public String getPath() {
    return path;
  }

  public String getContent() {
    return this.content;
  }

  @Override
  public List<ContainerEntry> getContainerEntries() {
    return Arrays.asList(new ContainerEntry() {
      @Override
      public long getSize() {
        return content.getBytes().length;
      }

      @Override
      public String getPath() {
        return path;
      }

      @Override
      public ContentSupplier getContentSupplier() {
        return () -> new ByteArrayInputStream(content.getBytes());
      }
    });
  }
}
