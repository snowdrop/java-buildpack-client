package dev.snowdrop.buildpack.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PingCmd;

@ExtendWith(MockitoExtension.class)
public class DockerConfigTest {
    @Test
    void checkTimeout() {
        DockerConfig dc1 = new DockerConfig(null, null, null, null, null, null, null, null, null);
        assertEquals(60, dc1.getPullTimeout());

        DockerConfig dc2 = new DockerConfig(245017, null, null, null, null, null, null, null, null);
        assertEquals(dc2.getPullTimeout(), 245017);
    }

    @Test
    void checkDockerHost(@Mock DockerClient dockerClient, @Mock PingCmd pingCmd) {
        lenient().when(dockerClient.pingCmd()).thenReturn(pingCmd);

        DockerConfig dc1 = new DockerConfig(null, null, null, null, null, null, null, null, null);
        assertNotNull(dc1.getDockerHost());

        DockerConfig dc2 = new DockerConfig(null, null, null, null, "tcp://stilettos", null, null, null, dockerClient);
        assertEquals("tcp://stilettos", dc2.getDockerHost());
    }

    @Test
    void checkDockerSocket(@Mock DockerClient dockerClient, @Mock PingCmd pingCmd) {

        lenient().when(dockerClient.pingCmd()).thenReturn(pingCmd);

        DockerConfig dc1 = new DockerConfig(null, null, null, null, null, null, null, null, null);
        assertNotNull(dc1.getDockerSocket());

        DockerConfig dc2 = new DockerConfig(null, null, null, null, "unix:///stilettos", null, null, null, dockerClient);
        assertEquals("/stilettos", dc2.getDockerSocket());

        DockerConfig dc3 = new DockerConfig(null, null, null, null, "tcp://stilettos", null, null, null, dockerClient);
        assertEquals("/var/run/docker.sock", dc3.getDockerSocket());

        DockerConfig dc4 = new DockerConfig(null, null, null, null, null, "fish", null, null, null);
        assertEquals("fish", dc4.getDockerSocket());
    }

    @Test
    void checkDockerNetwork() {
        DockerConfig dc1 = new DockerConfig(null, null, null, null, null, null, "kitten", null, null);
        assertEquals("kitten", dc1.getDockerNetwork());

        DockerConfig dc2 = new DockerConfig(null, null, null, null, null, null, null, null, null);
        assertNull(dc2.getDockerNetwork());
    }

    @Test
    void checkUseDaemon() {
        DockerConfig dc1 = new DockerConfig(null, null, null, null, null, null, null, null, null);
        assertTrue(dc1.getUseDaemon());

        DockerConfig dc2 = new DockerConfig(null, null, null, null, null, null, null, true, null);
        assertTrue(dc2.getUseDaemon());

        DockerConfig dc3 = new DockerConfig(null, null, null, null, null, null, null, false, null);
        assertFalse(dc3.getUseDaemon());
    }

    @Test
    void checkDockerClient(@Mock DockerClient dockerClient, @Mock PingCmd pingCmd){
        lenient().when(dockerClient.pingCmd()).thenReturn(pingCmd);

        DockerConfig dc1 = new DockerConfig(null, null, null, null, null, null, null, null, null);
        assertNotNull(dc1.getDockerClient());

        DockerConfig dc2 = new DockerConfig(null, null, null, null, null, null, null, null, dockerClient);
        assertEquals(dockerClient, dc2.getDockerClient());
    }

    @Test
    void checkPullPolicy(@Mock DockerClient dockerClient, @Mock PingCmd pingCmd){
        lenient().when(dockerClient.pingCmd()).thenReturn(pingCmd);

        DockerConfig dc1 = new DockerConfig(null, null, null, null, null, null, null, null, dockerClient);
        assertEquals(DockerConfig.PullPolicy.IF_NOT_PRESENT, dc1.getPullPolicy());

        DockerConfig dc2 = new DockerConfig(null, null, null, DockerConfig.PullPolicy.IF_NOT_PRESENT, null, null, null, null, dockerClient);
        assertEquals(DockerConfig.PullPolicy.IF_NOT_PRESENT, dc2.getPullPolicy());

        DockerConfig dc3 = new DockerConfig(null, null, null, DockerConfig.PullPolicy.ALWAYS, null, null, null, null, dockerClient);
        assertEquals(DockerConfig.PullPolicy.ALWAYS, dc3.getPullPolicy());        
    }  
    

    @Test
    void checkPullRetry(@Mock DockerClient dockerClient, @Mock PingCmd pingCmd){
        lenient().when(dockerClient.pingCmd()).thenReturn(pingCmd);

        DockerConfig dc1 = new DockerConfig(null, null, null, null, null, null, null, null, dockerClient);
        assertEquals(3, dc1.getPullRetryCount());

        DockerConfig dc2 = new DockerConfig(null, 5, null, null, null, null, null, null, dockerClient);
        assertEquals(5, dc2.getPullRetryCount());

        DockerConfig dc3 = new DockerConfig(null, 0, null, null, null, null, null, null, dockerClient);
        assertEquals(0, dc3.getPullRetryCount());        
    }     
}
