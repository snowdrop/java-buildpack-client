
package dev.snowdrop.buildpack.docker;

import java.util.List;

public interface Content {
  List<ContainerEntry> getContainerEntries();
}
