
package dev.snowdrop.buildpack.docker;

import java.util.Arrays;
import java.util.List;

import dev.snowdrop.buildpack.docker.ContainerEntry.ContentSupplier;
import io.sundr.builder.annotations.Buildable;

@Buildable(generateBuilderPackage = true, builderPackage = "dev.snowdrop.buildpack.builder")
public class StreamContent implements Content {

  private final String path;
  private final Long size;
  private final ContentSupplier contentSupplier;

  public StreamContent(String path, Long size, ContentSupplier contentSupplier) {
    this.path = path;
    this.size = size;
    this.contentSupplier = contentSupplier;
  }

  public List<ContainerEntry> getContainerEntries() {
    return Arrays.asList(new ContainerEntry() {
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
      });
  }
}
