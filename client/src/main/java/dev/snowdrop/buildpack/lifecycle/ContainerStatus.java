package dev.snowdrop.buildpack.lifecycle;

import lombok.Data;
import lombok.ToString;

@ToString(includeFieldNames=true)
@Data(staticConstructor="of")
public class ContainerStatus {
  final int rc;
  final String containerId;
}