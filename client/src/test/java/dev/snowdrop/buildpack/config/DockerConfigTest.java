package dev.snowdrop.buildpack.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PingCmd;

import dev.snowdrop.buildpack.docker.DockerClientUtils;

@ExtendWith(MockitoExtension.class)
public class DockerConfigTest {
    @Test
    void checkTimeout(@Mock DockerClient dockerClient, @Mock PingCmd pingCmd) {

        lenient().when(dockerClient.pingCmd()).thenReturn(pingCmd);
        lenient().when(pingCmd.exec()).thenAnswer(Answers.RETURNS_DEFAULTS);

        try (MockedStatic<? extends DockerClientUtils> clientUtils = mockStatic(DockerClientUtils.class) ) {
            DockerClientUtils.HostAndSocket hns = new DockerClientUtils.HostAndSocket("a","b");
            clientUtils.when(() -> DockerClientUtils.getDockerClient(eq(hns), any())).thenReturn(dockerClient);
            clientUtils.when(() -> DockerClientUtils.probeContainerRuntime(any())).thenReturn(hns);

            DockerConfig dc1 = new DockerConfig(null, null, null, null, null, null, null, null, null, null);
            assertEquals(60, dc1.getPullTimeoutSeconds());
    
            DockerConfig dc2 = new DockerConfig(245017, null, null, null, null, null, null, null, null, null);
            assertEquals(dc2.getPullTimeoutSeconds(), 245017);
        }
    }

    @Test
    void checkDockerHost(@Mock DockerClient dockerClient, @Mock PingCmd pingCmd) {
        lenient().when(dockerClient.pingCmd()).thenReturn(pingCmd);
        lenient().when(pingCmd.exec()).thenAnswer(Answers.RETURNS_DEFAULTS);

        try (MockedStatic<? extends DockerClientUtils> clientUtils = mockStatic(DockerClientUtils.class) ) {
            String dockerHost = "tcp://stilettos";
            DockerClientUtils.HostAndSocket hns = new DockerClientUtils.HostAndSocket(dockerHost,"b");
            clientUtils.when(() -> DockerClientUtils.getDockerClient(eq(hns), any())).thenReturn(dockerClient);
            clientUtils.when(() -> DockerClientUtils.probeContainerRuntime(any())).thenReturn(hns);

            DockerConfig dc1 = new DockerConfig(null, null, null, null, null, null, null, null, null, null);
            assertNotNull(dc1.getDockerHost());
    
            DockerConfig dc2 = new DockerConfig(null, null, null, null, dockerHost, null, null, null, dockerClient, null);
            assertEquals(dockerHost, dc2.getDockerHost());
        }
    }

    @Test
    void checkDockerSocket(@Mock DockerClient dockerClient, @Mock PingCmd pingCmd) {
        lenient().when(dockerClient.pingCmd()).thenReturn(pingCmd);
        lenient().when(pingCmd.exec()).thenAnswer(Answers.RETURNS_DEFAULTS);

        try (MockedStatic<? extends DockerClientUtils> clientUtils = mockStatic(DockerClientUtils.class) ) {
            String dockerSocket = "fish";
            DockerClientUtils.HostAndSocket hns = new DockerClientUtils.HostAndSocket("a",dockerSocket);
            clientUtils.when(() -> DockerClientUtils.getDockerClient(eq(hns), any())).thenReturn(dockerClient);
            clientUtils.when(() -> DockerClientUtils.probeContainerRuntime(any())).thenReturn(hns);

            DockerConfig dc1 = new DockerConfig(null, null, null, null, null, null, null, null, null, null);
            assertNotNull(dc1.getDockerSocket());
    
            DockerConfig dc4 = new DockerConfig(null, null, null, null, null, dockerSocket, null, null, null, null);
            assertEquals(dockerSocket, dc4.getDockerSocket());
        }
    }

    @Test
    void checkDockerNetwork(@Mock DockerClient dockerClient, @Mock PingCmd pingCmd) {
        lenient().when(dockerClient.pingCmd()).thenReturn(pingCmd);
        lenient().when(pingCmd.exec()).thenAnswer(Answers.RETURNS_DEFAULTS);

        try (MockedStatic<? extends DockerClientUtils> clientUtils = mockStatic(DockerClientUtils.class) ) {
            DockerClientUtils.HostAndSocket hns = new DockerClientUtils.HostAndSocket("a","b");
            clientUtils.when(() -> DockerClientUtils.getDockerClient(eq(hns), any())).thenReturn(dockerClient);
            clientUtils.when(() -> DockerClientUtils.probeContainerRuntime(any())).thenReturn(hns);

            DockerConfig dc1 = new DockerConfig(null, null, null, null, null, null, "kitten", null, null, null);
            assertEquals("kitten", dc1.getDockerNetwork());
    
            DockerConfig dc2 = new DockerConfig(null, null, null, null, null, null, null, null, null, null);
            assertNull(dc2.getDockerNetwork());
        }        
    }

    @Test
    void checkUseDaemon(@Mock DockerClient dockerClient, @Mock PingCmd pingCmd) {
        lenient().when(dockerClient.pingCmd()).thenReturn(pingCmd);
        lenient().when(pingCmd.exec()).thenAnswer(Answers.RETURNS_DEFAULTS);

        try (MockedStatic<? extends DockerClientUtils> clientUtils = mockStatic(DockerClientUtils.class) ) {
            DockerClientUtils.HostAndSocket hns = new DockerClientUtils.HostAndSocket("a","b");
            clientUtils.when(() -> DockerClientUtils.getDockerClient(eq(hns), any())).thenReturn(dockerClient);
            clientUtils.when(() -> DockerClientUtils.probeContainerRuntime(any())).thenReturn(hns);

            DockerConfig dc1 = new DockerConfig(null, null, null, null, null, null, null, null, null, null);
            assertTrue(dc1.getUseDaemon());
    
            DockerConfig dc2 = new DockerConfig(null, null, null, null, null, null, null, true, null, null);
            assertTrue(dc2.getUseDaemon());
    
            DockerConfig dc3 = new DockerConfig(null, null, null, null, null, null, null, false, null, null);
            assertFalse(dc3.getUseDaemon());
        }  
    }

    @Test
    void checkDockerClient(@Mock DockerClient dockerClient, @Mock PingCmd pingCmd){
        lenient().when(dockerClient.pingCmd()).thenReturn(pingCmd);
        lenient().when(pingCmd.exec()).thenAnswer(Answers.RETURNS_DEFAULTS);

        try (MockedStatic<? extends DockerClientUtils> clientUtils = mockStatic(DockerClientUtils.class) ) {
            DockerClientUtils.HostAndSocket hns = new DockerClientUtils.HostAndSocket("a","b");
            clientUtils.when(() -> DockerClientUtils.getDockerClient(eq(hns), any())).thenReturn(dockerClient);
            clientUtils.when(() -> DockerClientUtils.probeContainerRuntime(any())).thenReturn(hns);

            DockerConfig dc1 = new DockerConfig(null, null, null, null, null, null, null, null, null, null);
            assertNotNull(dc1.getDockerClient());
        }

        DockerConfig dc2 = new DockerConfig(null, null, null, null, null, null, null, null, dockerClient, null);
        assertEquals(dockerClient, dc2.getDockerClient());
    }

    @Test
    void checkPullPolicy(@Mock DockerClient dockerClient, @Mock PingCmd pingCmd){
        lenient().when(dockerClient.pingCmd()).thenReturn(pingCmd);
        lenient().when(pingCmd.exec()).thenAnswer(Answers.RETURNS_DEFAULTS);

        DockerConfig dc1 = new DockerConfig(null, null, null, null, null, null, null, null, dockerClient, null);
        assertEquals(DockerConfig.PullPolicy.IF_NOT_PRESENT, dc1.getPullPolicy());

        DockerConfig dc2 = new DockerConfig(null, null, null, DockerConfig.PullPolicy.IF_NOT_PRESENT, null, null, null, null, dockerClient, null);
        assertEquals(DockerConfig.PullPolicy.IF_NOT_PRESENT, dc2.getPullPolicy());

        DockerConfig dc3 = new DockerConfig(null, null, null, DockerConfig.PullPolicy.ALWAYS, null, null, null, null, dockerClient, null);
        assertEquals(DockerConfig.PullPolicy.ALWAYS, dc3.getPullPolicy());        
    }  
    

    @Test
    void checkPullRetry(@Mock DockerClient dockerClient, @Mock PingCmd pingCmd){
        lenient().when(dockerClient.pingCmd()).thenReturn(pingCmd);
        lenient().when(pingCmd.exec()).thenAnswer(Answers.RETURNS_DEFAULTS);

        DockerConfig dc1 = new DockerConfig(null, null, null, null, null, null, null, null, dockerClient, null);
        assertEquals(3, dc1.getPullRetryCount());

        DockerConfig dc2 = new DockerConfig(null, 5, null, null, null, null, null, null, dockerClient, null);
        assertEquals(5, dc2.getPullRetryCount());

        DockerConfig dc3 = new DockerConfig(null, 0, null, null, null, null, null, null, dockerClient, null);
        assertEquals(0, dc3.getPullRetryCount());        
    }     
}
