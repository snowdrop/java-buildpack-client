package dev.snowdrop.buildpack.lifecycle.phases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.command.WaitContainerCmd;
import com.github.dockerjava.api.command.WaitContainerResultCallback;

import dev.snowdrop.buildpack.BuilderImage;
import dev.snowdrop.buildpack.Logger;
import dev.snowdrop.buildpack.config.DockerConfig;
import dev.snowdrop.buildpack.config.ImageReference;
import dev.snowdrop.buildpack.config.LogConfig;
import dev.snowdrop.buildpack.lifecycle.ContainerStatus;
import dev.snowdrop.buildpack.lifecycle.LifecyclePhaseFactory;
import dev.snowdrop.buildpack.lifecycle.Version;

@ExtendWith(MockitoExtension.class)
public class ExtenderTest {

    @Captor
    ArgumentCaptor<String[]> argsCaptor;

    @Test
    void testPre12(@Mock LifecyclePhaseFactory factory, 
                  @Mock BuilderImage builder, 
                  @Mock LogConfig logConfig, 
                  @Mock DockerConfig dockerConfig, 
                  @Mock DockerClient dockerClient,
                  @Mock StartContainerCmd startCmd,
                  @Mock LogContainerCmd logCmd,
                  @Mock WaitContainerCmd waitCmd,
                  @Mock WaitContainerResultCallback waitResult,
                  @Mock Logger logger
                  ) {
        
        String  PLATFORM_LEVEL="0.10";
        String  LOG_LEVEL="debug";
        String  CONTAINER_ID="999";
        int     CONTAINER_RC=99;
        int     USER_ID=77;
        int     GROUP_ID=88;
        String  OUTPUT_IMAGE="stiletto";
        boolean USE_DAEMON=false;

        lenient().when(dockerConfig.getUseDaemon()).thenReturn(USE_DAEMON);
        lenient().when(dockerConfig.getDockerClient()).thenReturn(dockerClient);        
        lenient().when(factory.getDockerConfig()).thenReturn(dockerConfig);

        lenient().doNothing().when(startCmd).exec();
        lenient().when(dockerClient.startContainerCmd(any())).thenReturn(startCmd);

        lenient().when(logCmd.withFollowStream(any())).thenReturn(logCmd);
        lenient().when(logCmd.withStdOut(any())).thenReturn(logCmd);
        lenient().when(logCmd.withStdErr(any())).thenReturn(logCmd);
        lenient().when(logCmd.withTimestamps(any())).thenReturn(logCmd);
        lenient().when(logCmd.exec(any())).thenReturn(null);
        lenient().when(dockerClient.logContainerCmd(any())).thenReturn(logCmd);

        lenient().when(waitCmd.exec(any())).thenReturn(waitResult);
        lenient().when(waitResult.awaitStatusCode()).thenReturn(CONTAINER_RC);
        lenient().when(dockerClient.waitContainerCmd(any())).thenReturn(waitCmd);

        lenient().when(logConfig.getLogLevel()).thenReturn(LOG_LEVEL);
        lenient().when(factory.getLogConfig()).thenReturn(logConfig);

        lenient().when(builder.getUserId()).thenReturn(USER_ID);
        lenient().when(builder.getGroupId()).thenReturn(GROUP_ID);
        lenient().when(builder.getRunImages(any())).thenReturn(Stream.of("runImage1", "runImage2").collect(Collectors.toList()));
        lenient().when(factory.getBuilderImage()).thenReturn(builder);

        lenient().when(factory.getPlatformLevel()).thenReturn(new Version(PLATFORM_LEVEL));

        lenient().when(factory.getContainerForPhase(argsCaptor.capture(), any())).thenReturn(CONTAINER_ID);

        lenient().when(factory.getOutputImage()).thenReturn(new ImageReference(OUTPUT_IMAGE));

        Extender e = new Extender(factory, null);

        ContainerStatus cs = e.runPhase(logger, true);

        assertNotNull(cs);
        assertEquals(CONTAINER_ID, cs.getContainerId());
        assertEquals(CONTAINER_RC, cs.getRc());

        String[] args = argsCaptor.getValue();
        assertNotNull(args);
        //verify 1st & last elements
        assertEquals("/cnb/lifecycle/extender", args[0]);
        assertNotEquals(args[args.length-1], OUTPUT_IMAGE);

        List<String> argList = Arrays.asList(args);
        //verify kind not present in pre12
        assertFalse(argList.contains("-kind"));
        //verify log as expected
        assertTrue(argList.contains(LOG_LEVEL));

        verify(logCmd).withTimestamps(true);
        verify(dockerClient).logContainerCmd(CONTAINER_ID);
        verify(factory).getContainerForPhase(any(String[].class), eq(0)); //extender always runs as root
    }
    
    @Test
    void test12OnwardsBuild(@Mock LifecyclePhaseFactory factory, 
                  @Mock BuilderImage builder, 
                  @Mock LogConfig logConfig, 
                  @Mock DockerConfig dockerConfig, 
                  @Mock DockerClient dockerClient,
                  @Mock StartContainerCmd startCmd,
                  @Mock LogContainerCmd logCmd,
                  @Mock WaitContainerCmd waitCmd,
                  @Mock WaitContainerResultCallback waitResult,
                  @Mock Logger logger
                  ) {
        
        String  PLATFORM_LEVEL="0.12";
        String  LOG_LEVEL="debug";
        String  CONTAINER_ID="999";
        int     CONTAINER_RC=99;
        int     USER_ID=77;
        int     GROUP_ID=88;
        String  OUTPUT_IMAGE="stiletto";
        boolean USE_DAEMON=false;

        lenient().when(dockerConfig.getUseDaemon()).thenReturn(USE_DAEMON);
        lenient().when(dockerConfig.getDockerClient()).thenReturn(dockerClient);        
        lenient().when(factory.getDockerConfig()).thenReturn(dockerConfig);

        lenient().doNothing().when(startCmd).exec();
        lenient().when(dockerClient.startContainerCmd(any())).thenReturn(startCmd);

        lenient().when(logCmd.withFollowStream(any())).thenReturn(logCmd);
        lenient().when(logCmd.withStdOut(any())).thenReturn(logCmd);
        lenient().when(logCmd.withStdErr(any())).thenReturn(logCmd);
        lenient().when(logCmd.withTimestamps(any())).thenReturn(logCmd);
        lenient().when(logCmd.exec(any())).thenReturn(null);
        lenient().when(dockerClient.logContainerCmd(any())).thenReturn(logCmd);

        lenient().when(waitCmd.exec(any())).thenReturn(waitResult);
        lenient().when(waitResult.awaitStatusCode()).thenReturn(CONTAINER_RC);
        lenient().when(dockerClient.waitContainerCmd(any())).thenReturn(waitCmd);

        lenient().when(logConfig.getLogLevel()).thenReturn(LOG_LEVEL);
        lenient().when(factory.getLogConfig()).thenReturn(logConfig);

        lenient().when(builder.getUserId()).thenReturn(USER_ID);
        lenient().when(builder.getGroupId()).thenReturn(GROUP_ID);
        lenient().when(builder.getRunImages(any())).thenReturn(Stream.of("runImage1", "runImage2").collect(Collectors.toList()));
        lenient().when(factory.getBuilderImage()).thenReturn(builder);

        lenient().when(factory.getPlatformLevel()).thenReturn(new Version(PLATFORM_LEVEL));

        lenient().when(factory.getContainerForPhase(argsCaptor.capture(), any())).thenReturn(CONTAINER_ID);

        lenient().when(factory.getOutputImage()).thenReturn(new ImageReference(OUTPUT_IMAGE));

        Extender e = new Extender(factory, "build");

        ContainerStatus cs = e.runPhase(logger, true);

        assertNotNull(cs);
        assertEquals(CONTAINER_ID, cs.getContainerId());
        assertEquals(CONTAINER_RC, cs.getRc());

        String[] args = argsCaptor.getValue();
        assertNotNull(args);
        //verify 1st & last elements
        assertEquals("/cnb/lifecycle/extender", args[0]);
        assertNotEquals(args[args.length-1], OUTPUT_IMAGE);

        List<String> argList = Arrays.asList(args);
        //verify kind not present in pre12
        assertTrue(argList.contains("-kind"));
        assertEquals("build", argList.get(argList.indexOf("-kind")+1));
        //verify log as expected
        assertTrue(argList.contains(LOG_LEVEL));

        verify(logCmd).withTimestamps(true);
        verify(dockerClient).logContainerCmd(CONTAINER_ID);
        verify(factory).getContainerForPhase(any(String[].class), eq(0)); //extender always runs as root
    }

    @Test
    void test12OnwardsRun(@Mock LifecyclePhaseFactory factory, 
                  @Mock BuilderImage builder, 
                  @Mock LogConfig logConfig, 
                  @Mock DockerConfig dockerConfig, 
                  @Mock DockerClient dockerClient,
                  @Mock StartContainerCmd startCmd,
                  @Mock LogContainerCmd logCmd,
                  @Mock WaitContainerCmd waitCmd,
                  @Mock WaitContainerResultCallback waitResult,
                  @Mock Logger logger
                  ) {
        
        String  PLATFORM_LEVEL="0.12";
        String  LOG_LEVEL="debug";
        String  CONTAINER_ID="999";
        int     CONTAINER_RC=99;
        int     USER_ID=77;
        int     GROUP_ID=88;
        String  OUTPUT_IMAGE="stiletto";
        boolean USE_DAEMON=false;

        lenient().when(dockerConfig.getUseDaemon()).thenReturn(USE_DAEMON);
        lenient().when(dockerConfig.getDockerClient()).thenReturn(dockerClient);        
        lenient().when(factory.getDockerConfig()).thenReturn(dockerConfig);

        lenient().doNothing().when(startCmd).exec();
        lenient().when(dockerClient.startContainerCmd(any())).thenReturn(startCmd);

        lenient().when(logCmd.withFollowStream(any())).thenReturn(logCmd);
        lenient().when(logCmd.withStdOut(any())).thenReturn(logCmd);
        lenient().when(logCmd.withStdErr(any())).thenReturn(logCmd);
        lenient().when(logCmd.withTimestamps(any())).thenReturn(logCmd);
        lenient().when(logCmd.exec(any())).thenReturn(null);
        lenient().when(dockerClient.logContainerCmd(any())).thenReturn(logCmd);

        lenient().when(waitCmd.exec(any())).thenReturn(waitResult);
        lenient().when(waitResult.awaitStatusCode()).thenReturn(CONTAINER_RC);
        lenient().when(dockerClient.waitContainerCmd(any())).thenReturn(waitCmd);

        lenient().when(logConfig.getLogLevel()).thenReturn(LOG_LEVEL);
        lenient().when(factory.getLogConfig()).thenReturn(logConfig);

        lenient().when(builder.getUserId()).thenReturn(USER_ID);
        lenient().when(builder.getGroupId()).thenReturn(GROUP_ID);
        lenient().when(builder.getRunImages(any())).thenReturn(Stream.of("runImage1", "runImage2").collect(Collectors.toList()));
        lenient().when(factory.getBuilderImage()).thenReturn(builder);

        lenient().when(factory.getPlatformLevel()).thenReturn(new Version(PLATFORM_LEVEL));

        lenient().when(factory.getContainerForPhase(argsCaptor.capture(), any())).thenReturn(CONTAINER_ID);

        lenient().when(factory.getOutputImage()).thenReturn(new ImageReference(OUTPUT_IMAGE));

        Extender e = new Extender(factory, "run");

        ContainerStatus cs = e.runPhase(logger, true);

        assertNotNull(cs);
        assertEquals(CONTAINER_ID, cs.getContainerId());
        assertEquals(CONTAINER_RC, cs.getRc());

        String[] args = argsCaptor.getValue();
        assertNotNull(args);
        //verify 1st & last elements
        assertEquals("/cnb/lifecycle/extender", args[0]);
        assertNotEquals(args[args.length-1], OUTPUT_IMAGE);

        List<String> argList = Arrays.asList(args);
        //verify kind not present in pre12
        assertTrue(argList.contains("-kind"));
        assertEquals("run", argList.get(argList.indexOf("-kind")+1));
        //verify log as expected
        assertTrue(argList.contains(LOG_LEVEL));

        verify(logCmd).withTimestamps(true);
        verify(dockerClient).logContainerCmd(CONTAINER_ID);
        verify(factory).getContainerForPhase(any(String[].class), eq(0)); //extender always runs as root
    }    
}
