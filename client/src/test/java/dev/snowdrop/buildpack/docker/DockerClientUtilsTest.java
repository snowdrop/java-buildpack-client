package dev.snowdrop.buildpack.docker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.dockerjava.api.DockerClient;

import dev.snowdrop.buildpack.docker.DockerClientUtils.HostAndSocket;

@ExtendWith(MockitoExtension.class)
public class DockerClientUtilsTest {

  @Test
  void getDockerHost() {
    String val = System.getenv("DOCKER_HOST");

    HostAndSocket result = DockerClientUtils.probeContainerRuntime(null);

    if (val != null) {
      assertEquals(val, result.host);
    }

    assertNotNull(result);
  }

  @Test
  void getDockerClient() {
    DockerClient dc = DockerClientUtils.getDockerClient();
    assertNotNull(dc);
  }

}
