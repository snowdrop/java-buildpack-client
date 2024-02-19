package dev.snowdrop.buildpack.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.dockerjava.api.DockerClient;

@ExtendWith(MockitoExtension.class)
public class DockerConfigTest {
    @Test
    void checkTimeout() {
        DockerConfig dc1 = new DockerConfig(null, null, null, null, null, null);
        assertEquals(60, dc1.getPullTimeout());

        DockerConfig dc2 = new DockerConfig(245017, null, null, null, null, null);
        assertEquals(dc2.getPullTimeout(), 245017);
    }

    @Test
    void checkDockerHost() {
        DockerConfig dc1 = new DockerConfig(null, null, null, null, null, null);
        assertNotNull(dc1.getDockerHost());

        DockerConfig dc2 = new DockerConfig(null, "tcp://stilettos", null, null, null, null);
        assertEquals("tcp://stilettos", dc2.getDockerHost());
    }

    @Test
    void checkDockerSocket() {
        DockerConfig dc1 = new DockerConfig(null, null, null, null, null, null);
        assertNotNull(dc1.getDockerSocket());

        DockerConfig dc2 = new DockerConfig(null, "unix:///stilettos", null, null, null, null);
        assertEquals("/stilettos", dc2.getDockerSocket());

        DockerConfig dc3 = new DockerConfig(null, "tcp://stilettos", null, null, null, null);
        assertEquals("/var/run/docker.sock", dc3.getDockerSocket());

        DockerConfig dc4 = new DockerConfig(null, null, "fish", null, null, null);
        assertEquals("fish", dc4.getDockerSocket());
    }

    @Test
    void checkDockerNetwork() {
        DockerConfig dc1 = new DockerConfig(null, null, null, "kitten", null, null);
        assertEquals("kitten", dc1.getDockerNetwork());

        DockerConfig dc2 = new DockerConfig(null, null, null, null, null, null);
        assertNull(dc2.getDockerNetwork());
    }

    @Test
    void checkUseDaemon() {
        DockerConfig dc1 = new DockerConfig(null, null, null, null, null, null);
        assertTrue(dc1.getUseDaemon());

        DockerConfig dc2 = new DockerConfig(null, null, null, null, true, null);
        assertTrue(dc2.getUseDaemon());

        DockerConfig dc3 = new DockerConfig(null, null, null, null, false, null);
        assertFalse(dc3.getUseDaemon());
    }

    @Test
    void checkDockerClient(@Mock DockerClient dockerClient){
        DockerConfig dc1 = new DockerConfig(null, null, null, null, null, null);
        assertNotNull(dc1.getDockerClient());

        DockerConfig dc2 = new DockerConfig(null, null, null, null, null, dockerClient);
        assertEquals(dockerClient, dc2.getDockerClient());
    }
}
