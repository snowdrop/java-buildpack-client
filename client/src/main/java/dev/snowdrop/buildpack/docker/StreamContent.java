
package dev.snowdrop.buildpack.docker;

import java.util.Arrays;
import java.util.List;

import dev.snowdrop.buildpack.docker.ContainerEntry.DataSupplier;
import io.sundr.builder.annotations.Buildable;

@Buildable(generateBuilderPackage = true, builderPackage = "dev.snowdrop.buildpack.builder")
public class StreamContent implements Content {

  private final String path;
  private final Long size;
  private final Integer mode;
  private final DataSupplier dataSupplier;

  public StreamContent(String path, Long size, Integer mode, DataSupplier dataSupplier) {
    this.path = path;
    this.size = size;
    this.mode = mode;
    this.dataSupplier = dataSupplier;
  }

  public List<ContainerEntry> getContainerEntries() {
    return Arrays.asList(new ContainerEntry() {
        @Override
        public DataSupplier getDataSupplier() {
          return dataSupplier;
        }

        @Override
        public String getPath() {
          return path;
        }
        
        @Override
        public long getSize() {
          return size;
        }

        @Override
        public Integer getMode() {
          return mode;
        }
      });
  }
}
