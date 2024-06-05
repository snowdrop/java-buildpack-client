package dev.snowdrop.buildpack.lifecycle.phases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
public class ExporterTest {

    @Captor
    ArgumentCaptor<String[]> argsCaptor;

    @Test
    void testPre7(@Mock LifecyclePhaseFactory factory, 
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
        
        String  PLATFORM_LEVEL="0.6";
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

        Exporter e = new Exporter(factory, false);

        ContainerStatus cs = e.runPhase(logger, true);

        assertNotNull(cs);
        assertEquals(CONTAINER_ID, cs.getContainerId());
        assertEquals(CONTAINER_RC, cs.getRc());

        String[] args = argsCaptor.getValue();
        assertNotNull(args);
        //verify 1st & last elements
        assertEquals("/cnb/lifecycle/exporter", args[0]);
        assertEquals(args[args.length-1], OUTPUT_IMAGE);

        List<String> argList = Arrays.asList(args);
        //verify run-image is used for a pre7 run
        assertTrue(argList.contains("-run-image"));
        assertFalse(argList.contains("-run"));
        //verify no dameon
        assertFalse(argList.contains("-daemon"));
        //verify log as expected
        assertTrue(argList.contains(LOG_LEVEL));

        verify(logCmd).withTimestamps(true);
        verify(dockerClient).logContainerCmd(CONTAINER_ID);
        verify(factory).getContainerForPhase(any(String[].class), eq(USER_ID));
    }

    @Test
    void test7Onwards(@Mock LifecyclePhaseFactory factory, 
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
        
        String  PLATFORM_LEVEL="0.7";
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

        Exporter e = new Exporter(factory, false);

        ContainerStatus cs = e.runPhase(logger, true);

        assertNotNull(cs);
        assertEquals(CONTAINER_ID, cs.getContainerId());
        assertEquals(CONTAINER_RC, cs.getRc());

        String[] args = argsCaptor.getValue();
        assertNotNull(args);
        //verify 1st & last elements
        assertEquals("/cnb/lifecycle/exporter", args[0]);
        assertEquals(args[args.length-1], OUTPUT_IMAGE);

        List<String> argList = Arrays.asList(args);
        //verify run-image is not used for a 7 onwards run
        assertFalse(argList.contains("-run-image"));
        assertFalse(argList.contains("-run"));
        //verify no dameon
        assertFalse(argList.contains("-daemon"));
        //verify log as expected
        assertTrue(argList.contains(LOG_LEVEL));

        verify(logCmd).withTimestamps(true);
        verify(dockerClient).logContainerCmd(CONTAINER_ID);
        verify(factory).getContainerForPhase(any(String[].class), eq(USER_ID));
    }    

    @Test
    void test12OnwardsNoRunExt(@Mock LifecyclePhaseFactory factory, 
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

        Exporter e = new Exporter(factory, false);

        ContainerStatus cs = e.runPhase(logger, true);

        assertNotNull(cs);
        assertEquals(CONTAINER_ID, cs.getContainerId());
        assertEquals(CONTAINER_RC, cs.getRc());

        String[] args = argsCaptor.getValue();
        assertNotNull(args);
        //verify 1st & last elements
        assertEquals("/cnb/lifecycle/exporter", args[0]);
        assertEquals(args[args.length-1], OUTPUT_IMAGE);

        List<String> argList = Arrays.asList(args);
        //verify run is not used for a 12 run with no run extns
        assertFalse(argList.contains("-run-image"));
        assertFalse(argList.contains("-run"));
        //verify no dameon
        assertFalse(argList.contains("-daemon"));
        //verify log as expected
        assertTrue(argList.contains(LOG_LEVEL));

        verify(logCmd).withTimestamps(true);
        verify(dockerClient).logContainerCmd(CONTAINER_ID);
        verify(factory).getContainerForPhase(any(String[].class), eq(USER_ID));
    }   

    @Test
    void test12OnwardsRunExt(@Mock LifecyclePhaseFactory factory, 
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

        Exporter e = new Exporter(factory, true);

        ContainerStatus cs = e.runPhase(logger, true);

        assertNotNull(cs);
        assertEquals(CONTAINER_ID, cs.getContainerId());
        assertEquals(CONTAINER_RC, cs.getRc());

        String[] args = argsCaptor.getValue();
        assertNotNull(args);
        //verify 1st & last elements
        assertEquals("/cnb/lifecycle/exporter", args[0]);
        assertEquals(args[args.length-1], OUTPUT_IMAGE);

        List<String> argList = Arrays.asList(args);
        //verify run is not used for a 12 run with no run extns
        assertFalse(argList.contains("-run-image"));
        assertTrue(argList.contains("-run"));
        //verify no dameon
        assertFalse(argList.contains("-daemon"));
        //verify log as expected
        assertTrue(argList.contains(LOG_LEVEL));

        verify(logCmd).withTimestamps(true);
        verify(dockerClient).logContainerCmd(CONTAINER_ID);
        verify(factory).getContainerForPhase(any(String[].class), eq(USER_ID));
    }       

    @Test
    void testDisableTimestamps(@Mock LifecyclePhaseFactory factory, 
                  @Mock BuilderImage builder, 
                  @Mock LogConfig logConfig, 
                  @Mock DockerConfig dockerConfig, 
                  @Mock DockerClient dockerClient,
                  @Mock StartContainerCmd startCmd,
                  @Mock LogContainerCmd logCmd,
                  @Mock WaitContainerCmd waitCmd,
                  @Mock WaitContainerResultCallback waitResult,
                  @Mock Logger logger
                  ) 
    {
        
        String  PLATFORM_LEVEL="0.9";
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


        Exporter e = new Exporter(factory, false);

        ContainerStatus cs = e.runPhase(logger, false);

        assertNotNull(cs);
        assertEquals(CONTAINER_ID, cs.getContainerId());
        assertEquals(CONTAINER_RC, cs.getRc());

        String[] args = argsCaptor.getValue();
        assertNotNull(args);
        //verify 1st & last elements
        assertEquals("/cnb/lifecycle/exporter", args[0]);
        assertEquals(args[args.length-1], OUTPUT_IMAGE);

        List<String> argList = Arrays.asList(args);
        //verify daemon
        assertFalse(argList.contains("-daemon"));
        //verify log as expected
        assertTrue(argList.contains(LOG_LEVEL));

        verify(logCmd).withTimestamps(false);
        verify(dockerClient).logContainerCmd(CONTAINER_ID);
        verify(factory).getContainerForPhase(any(String[].class), eq(USER_ID));
    }  

    @Test
    void testWithDaemon(@Mock LifecyclePhaseFactory factory, 
                  @Mock BuilderImage builder, 
                  @Mock LogConfig logConfig, 
                  @Mock DockerConfig dockerConfig, 
                  @Mock DockerClient dockerClient,
                  @Mock StartContainerCmd startCmd,
                  @Mock LogContainerCmd logCmd,
                  @Mock WaitContainerCmd waitCmd,
                  @Mock WaitContainerResultCallback waitResult,
                  @Mock Logger logger
                  ) 
    {
        
        String  PLATFORM_LEVEL="0.9";
        String  LOG_LEVEL="debug";
        String  CONTAINER_ID="999";
        int     CONTAINER_RC=99;
        int     USER_ID=77;
        int     GROUP_ID=88;
        String  OUTPUT_IMAGE="stiletto";
        boolean USE_DAEMON=true;

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

        Exporter e = new Exporter(factory, false);

        ContainerStatus cs = e.runPhase(logger, true);

        assertNotNull(cs);
        assertEquals(CONTAINER_ID, cs.getContainerId());
        assertEquals(CONTAINER_RC, cs.getRc());

        String[] args = argsCaptor.getValue();
        assertNotNull(args);
        //verify 1st & last elements
        assertEquals("/cnb/lifecycle/exporter", args[0]);
        assertEquals(args[args.length-1], OUTPUT_IMAGE);

        List<String> argList = Arrays.asList(args);
        //verify dameon
        assertTrue(argList.contains("-daemon"));
        //verify log as expected
        assertTrue(argList.contains(LOG_LEVEL));

        verify(logCmd).withTimestamps(true);
        verify(dockerClient).logContainerCmd(CONTAINER_ID);
        verify(factory).getContainerForPhase(any(String[].class), eq(0));
    }     

}
