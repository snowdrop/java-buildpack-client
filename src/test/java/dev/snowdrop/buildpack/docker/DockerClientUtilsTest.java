package dev.snowdrop.buildpack.docker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.dockerjava.api.DockerClient;

@ExtendWith(MockitoExtension.class)
public class DockerClientUtilsTest {

  @Test
  void getDockerHost() {
    String val = System.getenv("DOCKER_HOST");

    String result = DockerClientUtils.getDockerHost();

    if (val != null) {
      assertEquals(val, result);
    }

    assertNotNull(result);
  }

  @Test
  void getDockerClient() {
    DockerClient dc = DockerClientUtils.getDockerClient();
    assertNotNull(dc);
  }

}
