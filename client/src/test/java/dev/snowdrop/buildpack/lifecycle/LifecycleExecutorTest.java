package dev.snowdrop.buildpack.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.commons.util.ReflectionUtils;
import org.mockito.Answers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.command.WaitContainerCmd;
import com.github.dockerjava.api.command.WaitContainerResultCallback;

import dev.snowdrop.buildpack.BuildConfig;
import dev.snowdrop.buildpack.BuilderImage;
import dev.snowdrop.buildpack.Logger;
import dev.snowdrop.buildpack.config.CacheConfig;
import dev.snowdrop.buildpack.config.DockerConfig;
import dev.snowdrop.buildpack.config.ImageReference;
import dev.snowdrop.buildpack.config.LogConfig;
import dev.snowdrop.buildpack.config.PlatformConfig;
import dev.snowdrop.buildpack.docker.ContainerUtils;
import dev.snowdrop.buildpack.docker.ImageUtils;
import dev.snowdrop.buildpack.lifecycle.phases.Analyzer;
import dev.snowdrop.buildpack.lifecycle.phases.Builder;
import dev.snowdrop.buildpack.lifecycle.phases.Creator;
import dev.snowdrop.buildpack.lifecycle.phases.Detector;
import dev.snowdrop.buildpack.lifecycle.phases.Exporter;
import dev.snowdrop.buildpack.lifecycle.phases.Extender;
import dev.snowdrop.buildpack.lifecycle.phases.Restorer;

@ExtendWith(MockitoExtension.class)
public class LifecycleExecutorTest {
    
    @SuppressWarnings("resource")
    @Test
    void testPre7(
                  @Mock LifecyclePhaseFactory lifecycleFactory, 
                  @Mock BuilderImage extendedBuilder, 
                  @Mock BuilderImage origBuilder,
                  @Mock LogConfig logConfig,
                  @Mock CacheConfig buildCacheConfig,
                  @Mock CacheConfig launchCacheConfig,
                  @Mock CacheConfig kanikoCacheConfig,
                  @Mock PlatformConfig platformConfig,
                  @Mock DockerConfig dockerConfig, 
                  @Mock DockerClient dockerClient,
                  @Mock StartContainerCmd startCmd,
                  @Mock LogContainerCmd logCmd,
                  @Mock WaitContainerCmd waitCmd,
                  @Mock WaitContainerResultCallback waitResult,
                  @Mock Logger logger,
                  @Mock BuildConfig config,
                  @Mock Analyzer analyzer,
                  @Mock Builder builder,
                  @Mock Creator creator,
                  @Mock Detector detector,
                  @Mock Exporter exporter,
                  @Mock Extender extender,
                  @Mock Restorer restorer
    ){
        String PLATFORM_LEVEL="0.6";
        String OUTPUT_IMAGE="fish";
        boolean HAS_EXTENSIONS=false;

        List<String> runImages = new ArrayList<>();
        runImages.add("run-image");

        lenient().when(logConfig.getUseTimestamps()).thenReturn(true);
        lenient().when(logConfig.getLogger()).thenReturn(logger);

        lenient().when(dockerConfig.getDockerClient()).thenReturn(dockerClient);
        lenient().when(dockerConfig.getPullTimeoutSeconds()).thenReturn(66);
        lenient().when(dockerConfig.getPullPolicy()).thenReturn(DockerConfig.PullPolicy.IF_NOT_PRESENT);

        lenient().when(config.getDockerConfig()).thenReturn(dockerConfig);
        lenient().when(config.getBuildCacheConfig()).thenReturn(buildCacheConfig);
        lenient().when(config.getLaunchCacheConfig()).thenReturn(launchCacheConfig);
        lenient().when(config.getKanikoCacheConfig()).thenReturn(kanikoCacheConfig);
        lenient().when(config.getPlatformConfig()).thenReturn(platformConfig);
        lenient().when(config.getLogConfig()).thenReturn(logConfig);
        lenient().when(config.getOutputImage()).thenReturn(new ImageReference(OUTPUT_IMAGE));

        lenient().doNothing().when(lifecycleFactory).createVolumes(any());
        lenient().doNothing().when(lifecycleFactory).tidyUp();
        lenient().when(lifecycleFactory.getAnalyzer()).thenReturn(analyzer);
        lenient().when(lifecycleFactory.getDetector()).thenReturn(detector);
        lenient().when(lifecycleFactory.getBuilder()).thenReturn(builder);
        lenient().when(lifecycleFactory.getCreator()).thenReturn(creator);
        lenient().when(lifecycleFactory.getExporter(false)).thenReturn(exporter);
        lenient().when(lifecycleFactory.getExtender(any())).thenReturn(extender);
        lenient().when(lifecycleFactory.getRestorer()).thenReturn(restorer);
        lenient().when(lifecycleFactory.getBuilderImage()).thenReturn(extendedBuilder);

        lenient().when(extendedBuilder.hasExtensions()).thenReturn(HAS_EXTENSIONS);
        lenient().when(extendedBuilder.getRunImages(any())).thenReturn(runImages);

        lenient().when(analyzer.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"analyzer-id"));
        lenient().when(detector.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"detector-id"));
        lenient().when(detector.getAnalyzedToml()).thenReturn("[run-image]\n reference=newfish\n extend=false".getBytes());
        lenient().when(builder.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"builder-id"));
        lenient().when(creator.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"creator-id"));
        lenient().when(exporter.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"exporter-id"));
        lenient().when(extender.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"extender-id"));
        lenient().when(restorer.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"restorer-id"));

        try (MockedStatic<? extends ContainerUtils> containerUtils = mockStatic(ContainerUtils.class);
            MockedStatic<? extends ImageUtils> imageUtils = mockStatic(ImageUtils.class)) {

            containerUtils.when(() -> ContainerUtils.removeContainer(eq(dockerClient), any())).thenAnswer(Answers.RETURNS_DEFAULTS);
            imageUtils.when(() -> ImageUtils.pullImages(dockerConfig, "newfish")).thenAnswer(Answers.RETURNS_DEFAULTS);

            LifecycleExecutor le = new LifecycleExecutor(config, extendedBuilder, origBuilder, PLATFORM_LEVEL);
            
            try{
                Field factory = ReflectionUtils.findFields(LifecycleExecutor.class, 
                                                        f->f.getName().equals("factory"), 
                                                        ReflectionUtils.HierarchyTraversalMode.TOP_DOWN)
                                            .get(0);
                factory.setAccessible(true);
                factory.set(le, lifecycleFactory);
            }catch(Exception e){
                fail();
            }


            le.execute();

            //ensure correct phases driven in expected order for this platform revision.
            InOrder order = Mockito.inOrder(detector,analyzer,restorer,builder,exporter);
            order.verify(detector).runPhase(logger, true);
            order.verify(analyzer).runPhase(logger, true);
            order.verify(restorer).runPhase(logger, true);
            order.verify(builder).runPhase(logger, true);
            order.verify(exporter).runPhase(logger, true);
        }   
    }

    @SuppressWarnings("resource")
    @Test
    void test7Onwards(
                  @Mock LifecyclePhaseFactory lifecycleFactory, 
                  @Mock BuilderImage extendedBuilder, 
                  @Mock BuilderImage origBuilder,
                  @Mock LogConfig logConfig,
                  @Mock CacheConfig buildCacheConfig,
                  @Mock CacheConfig launchCacheConfig,
                  @Mock CacheConfig kanikoCacheConfig,
                  @Mock PlatformConfig platformConfig,
                  @Mock DockerConfig dockerConfig, 
                  @Mock DockerClient dockerClient,
                  @Mock StartContainerCmd startCmd,
                  @Mock LogContainerCmd logCmd,
                  @Mock WaitContainerCmd waitCmd,
                  @Mock WaitContainerResultCallback waitResult,
                  @Mock Logger logger,
                  @Mock BuildConfig config,
                  @Mock Analyzer analyzer,
                  @Mock Builder builder,
                  @Mock Creator creator,
                  @Mock Detector detector,
                  @Mock Exporter exporter,
                  @Mock Extender extender,
                  @Mock Restorer restorer
    ){
        String PLATFORM_LEVEL="0.7";
        String OUTPUT_IMAGE="fish";
        boolean HAS_EXTENSIONS=false;

        List<String> runImages = new ArrayList<>();
        runImages.add("run-image");

        lenient().when(logConfig.getUseTimestamps()).thenReturn(true);
        lenient().when(logConfig.getLogger()).thenReturn(logger);

        lenient().when(dockerConfig.getDockerClient()).thenReturn(dockerClient);
        lenient().when(dockerConfig.getPullTimeoutSeconds()).thenReturn(66);
        lenient().when(dockerConfig.getPullPolicy()).thenReturn(DockerConfig.PullPolicy.IF_NOT_PRESENT);

        lenient().when(config.getDockerConfig()).thenReturn(dockerConfig);
        lenient().when(config.getBuildCacheConfig()).thenReturn(buildCacheConfig);
        lenient().when(config.getLaunchCacheConfig()).thenReturn(launchCacheConfig);
        lenient().when(config.getKanikoCacheConfig()).thenReturn(kanikoCacheConfig);
        lenient().when(config.getPlatformConfig()).thenReturn(platformConfig);
        lenient().when(config.getLogConfig()).thenReturn(logConfig);
        lenient().when(config.getOutputImage()).thenReturn(new ImageReference(OUTPUT_IMAGE));

        lenient().doNothing().when(lifecycleFactory).createVolumes(any());
        lenient().doNothing().when(lifecycleFactory).tidyUp();
        lenient().when(lifecycleFactory.getAnalyzer()).thenReturn(analyzer);
        lenient().when(lifecycleFactory.getDetector()).thenReturn(detector);
        lenient().when(lifecycleFactory.getBuilder()).thenReturn(builder);
        lenient().when(lifecycleFactory.getCreator()).thenReturn(creator);
        lenient().when(lifecycleFactory.getExporter(false)).thenReturn(exporter);
        lenient().when(lifecycleFactory.getExtender(any())).thenReturn(extender);
        lenient().when(lifecycleFactory.getRestorer()).thenReturn(restorer);
        lenient().when(lifecycleFactory.getBuilderImage()).thenReturn(extendedBuilder);

        lenient().when(extendedBuilder.hasExtensions()).thenReturn(HAS_EXTENSIONS);
        lenient().when(extendedBuilder.getRunImages(any())).thenReturn(runImages);

        lenient().when(analyzer.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"analyzer-id"));
        lenient().when(detector.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"detector-id"));
        lenient().when(detector.getAnalyzedToml()).thenReturn("[run-image]\n reference=newfish\n extend=false".getBytes());
        lenient().when(builder.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"builder-id"));
        lenient().when(creator.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"creator-id"));
        lenient().when(exporter.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"exporter-id"));
        lenient().when(extender.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"extender-id"));
        lenient().when(restorer.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"restorer-id"));

        try (MockedStatic<? extends ContainerUtils> containerUtils = mockStatic(ContainerUtils.class);
            MockedStatic<? extends ImageUtils> imageUtils = mockStatic(ImageUtils.class)) {

            containerUtils.when(() -> ContainerUtils.removeContainer(eq(dockerClient), any())).thenAnswer(Answers.RETURNS_DEFAULTS);
            imageUtils.when(() -> ImageUtils.pullImages(dockerConfig,"newfish")).thenAnswer(Answers.RETURNS_DEFAULTS);

            LifecycleExecutor le = new LifecycleExecutor(config, extendedBuilder, origBuilder, PLATFORM_LEVEL);
            
            try{
                Field factory = ReflectionUtils.findFields(LifecycleExecutor.class, 
                                                        f->f.getName().equals("factory"), 
                                                        ReflectionUtils.HierarchyTraversalMode.TOP_DOWN)
                                            .get(0);
                factory.setAccessible(true);
                factory.set(le, lifecycleFactory);
            }catch(Exception e){
                fail();
            }


            le.execute();

            //ensure correct phases driven in expected order for this platform revision.
            InOrder order = Mockito.inOrder(detector,analyzer,restorer,builder,exporter);
            order.verify(analyzer).runPhase(logger, true);            
            order.verify(detector).runPhase(logger, true);
            order.verify(restorer).runPhase(logger, true);
            order.verify(builder).runPhase(logger, true);
            order.verify(exporter).runPhase(logger, true);
        }   
    } 

    @SuppressWarnings("resource")
    @Test
    void test10OnwardsNoXtns(
                  @Mock LifecyclePhaseFactory lifecycleFactory, 
                  @Mock BuilderImage extendedBuilder, 
                  @Mock BuilderImage origBuilder,
                  @Mock LogConfig logConfig,
                  @Mock CacheConfig buildCacheConfig,
                  @Mock CacheConfig launchCacheConfig,
                  @Mock CacheConfig kanikoCacheConfig,
                  @Mock PlatformConfig platformConfig,
                  @Mock DockerConfig dockerConfig, 
                  @Mock DockerClient dockerClient,
                  @Mock StartContainerCmd startCmd,
                  @Mock LogContainerCmd logCmd,
                  @Mock WaitContainerCmd waitCmd,
                  @Mock WaitContainerResultCallback waitResult,
                  @Mock Logger logger,
                  @Mock BuildConfig config,
                  @Mock Analyzer analyzer,
                  @Mock Builder builder,
                  @Mock Creator creator,
                  @Mock Detector detector,
                  @Mock Exporter exporter,
                  @Mock Extender extender,
                  @Mock Restorer restorer
    ){
        String PLATFORM_LEVEL="0.10";
        String OUTPUT_IMAGE="fish";
        boolean HAS_EXTENSIONS=false;

        List<String> runImages = new ArrayList<>();
        runImages.add("run-image");

        lenient().when(logConfig.getUseTimestamps()).thenReturn(true);
        lenient().when(logConfig.getLogger()).thenReturn(logger);

        lenient().when(dockerConfig.getPullPolicy()).thenReturn(DockerConfig.PullPolicy.IF_NOT_PRESENT);
        lenient().when(dockerConfig.getDockerClient()).thenReturn(dockerClient);
        lenient().when(dockerConfig.getPullTimeoutSeconds()).thenReturn(66);

        lenient().when(config.getDockerConfig()).thenReturn(dockerConfig);
        lenient().when(config.getBuildCacheConfig()).thenReturn(buildCacheConfig);
        lenient().when(config.getLaunchCacheConfig()).thenReturn(launchCacheConfig);
        lenient().when(config.getKanikoCacheConfig()).thenReturn(kanikoCacheConfig);
        lenient().when(config.getPlatformConfig()).thenReturn(platformConfig);
        lenient().when(config.getLogConfig()).thenReturn(logConfig);
        lenient().when(config.getOutputImage()).thenReturn(new ImageReference(OUTPUT_IMAGE));

        lenient().doNothing().when(lifecycleFactory).createVolumes(any());
        lenient().doNothing().when(lifecycleFactory).tidyUp();
        lenient().when(lifecycleFactory.getAnalyzer()).thenReturn(analyzer);
        lenient().when(lifecycleFactory.getDetector()).thenReturn(detector);
        lenient().when(lifecycleFactory.getBuilder()).thenReturn(builder);
        lenient().when(lifecycleFactory.getCreator()).thenReturn(creator);
        lenient().when(lifecycleFactory.getExporter(false)).thenReturn(exporter);
        lenient().when(lifecycleFactory.getExtender(any())).thenReturn(extender);
        lenient().when(lifecycleFactory.getRestorer()).thenReturn(restorer);
        lenient().when(lifecycleFactory.getBuilderImage()).thenReturn(extendedBuilder);

        lenient().when(extendedBuilder.hasExtensions()).thenReturn(HAS_EXTENSIONS);
        lenient().when(extendedBuilder.getRunImages(any())).thenReturn(runImages);

        lenient().when(analyzer.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"analyzer-id"));
        lenient().when(detector.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"detector-id"));
        lenient().when(detector.getAnalyzedToml()).thenReturn("[run-image]\n reference=newfish\n extend=false".getBytes());
        lenient().when(builder.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"builder-id"));
        lenient().when(creator.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"creator-id"));
        lenient().when(exporter.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"exporter-id"));
        lenient().when(extender.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"extender-id"));
        lenient().when(restorer.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"restorer-id"));

        try (MockedStatic<? extends ContainerUtils> containerUtils = mockStatic(ContainerUtils.class);
            MockedStatic<? extends ImageUtils> imageUtils = mockStatic(ImageUtils.class)) {

            containerUtils.when(() -> ContainerUtils.removeContainer(eq(dockerClient), any())).thenAnswer(Answers.RETURNS_DEFAULTS);
            imageUtils.when(() -> ImageUtils.pullImages(dockerConfig, "newfish")).thenAnswer(Answers.RETURNS_DEFAULTS);

            LifecycleExecutor le = new LifecycleExecutor(config, extendedBuilder, origBuilder, PLATFORM_LEVEL);
            
            try{
                Field factory = ReflectionUtils.findFields(LifecycleExecutor.class, 
                                                        f->f.getName().equals("factory"), 
                                                        ReflectionUtils.HierarchyTraversalMode.TOP_DOWN)
                                            .get(0);
                factory.setAccessible(true);
                factory.set(le, lifecycleFactory);
            }catch(Exception e){
                fail();
            }


            le.execute();

            //ensure correct phases driven in expected order for this platform revision.
            InOrder order = Mockito.inOrder(detector,analyzer,restorer,builder,exporter);
            order.verify(analyzer).runPhase(logger, true);            
            order.verify(detector).runPhase(logger, true);
            order.verify(restorer).runPhase(logger, true);
            order.verify(builder).runPhase(logger, true);
            order.verify(exporter).runPhase(logger, true);
        }   
    }      

    @SuppressWarnings("resource")
    @Test
    void test10OnwardsXtns(
                  @Mock LifecyclePhaseFactory lifecycleFactory, 
                  @Mock BuilderImage extendedBuilder, 
                  @Mock BuilderImage origBuilder,
                  @Mock LogConfig logConfig,
                  @Mock CacheConfig buildCacheConfig,
                  @Mock CacheConfig launchCacheConfig,
                  @Mock CacheConfig kanikoCacheConfig,
                  @Mock PlatformConfig platformConfig,
                  @Mock DockerConfig dockerConfig, 
                  @Mock DockerClient dockerClient,
                  @Mock StartContainerCmd startCmd,
                  @Mock LogContainerCmd logCmd,
                  @Mock WaitContainerCmd waitCmd,
                  @Mock WaitContainerResultCallback waitResult,
                  @Mock Logger logger,
                  @Mock BuildConfig config,
                  @Mock Analyzer analyzer,
                  @Mock Builder builder,
                  @Mock Creator creator,
                  @Mock Detector detector,
                  @Mock Exporter exporter,
                  @Mock Extender runExtender,
                  @Mock Extender buildExtender,
                  @Mock Restorer restorer
    ){
        String PLATFORM_LEVEL="0.10";
        String OUTPUT_IMAGE="fish";
        boolean HAS_EXTENSIONS=true;

        List<String> runImages = new ArrayList<>();
        runImages.add("run-image");

        lenient().when(logConfig.getUseTimestamps()).thenReturn(true);
        lenient().when(logConfig.getLogger()).thenReturn(logger);

        lenient().when(dockerConfig.getPullPolicy()).thenReturn(DockerConfig.PullPolicy.IF_NOT_PRESENT);
        lenient().when(dockerConfig.getDockerClient()).thenReturn(dockerClient);
        lenient().when(dockerConfig.getPullTimeoutSeconds()).thenReturn(66);

        lenient().when(config.getDockerConfig()).thenReturn(dockerConfig);
        lenient().when(config.getBuildCacheConfig()).thenReturn(buildCacheConfig);
        lenient().when(config.getLaunchCacheConfig()).thenReturn(launchCacheConfig);
        lenient().when(config.getKanikoCacheConfig()).thenReturn(kanikoCacheConfig);
        lenient().when(config.getPlatformConfig()).thenReturn(platformConfig);
        lenient().when(config.getLogConfig()).thenReturn(logConfig);
        lenient().when(config.getOutputImage()).thenReturn(new ImageReference(OUTPUT_IMAGE));

        lenient().doNothing().when(lifecycleFactory).createVolumes(any());
        lenient().doNothing().when(lifecycleFactory).tidyUp();
        lenient().when(lifecycleFactory.getAnalyzer()).thenReturn(analyzer);
        lenient().when(lifecycleFactory.getDetector()).thenReturn(detector);
        lenient().when(lifecycleFactory.getBuilder()).thenReturn(builder);
        lenient().when(lifecycleFactory.getCreator()).thenReturn(creator);
        lenient().when(lifecycleFactory.getExporter(false)).thenReturn(exporter);
        lenient().when(lifecycleFactory.getRunImageExtender()).thenReturn(runExtender);
        lenient().when(lifecycleFactory.getBuildImageExtender()).thenReturn(buildExtender);
        lenient().when(lifecycleFactory.getRestorer()).thenReturn(restorer);
        lenient().when(lifecycleFactory.getBuilderImage()).thenReturn(extendedBuilder);
        lenient().when(lifecycleFactory.getPlatformLevel()).thenReturn(new Version(PLATFORM_LEVEL));
        lenient().when(lifecycleFactory.getDockerConfig()).thenReturn(dockerConfig);

        lenient().when(extendedBuilder.hasExtensions()).thenReturn(HAS_EXTENSIONS);
        lenient().when(extendedBuilder.getRunImages(any())).thenReturn(runImages);

        lenient().when(analyzer.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"analyzer-id"));
        lenient().when(detector.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"detector-id"));
        lenient().when(detector.getAnalyzedToml()).thenReturn("[run-image]\nreference = \"newfish\"\nextend = false".getBytes());
        lenient().when(builder.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"builder-id"));
        lenient().when(creator.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"creator-id"));
        lenient().when(exporter.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"exporter-id"));
        lenient().when(runExtender.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"run-extender-id"));
        lenient().when(buildExtender.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"build-extender-id"));
        lenient().when(restorer.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"restorer-id"));

        try (MockedStatic<? extends ContainerUtils> containerUtils = mockStatic(ContainerUtils.class);
            MockedStatic<? extends ImageUtils> imageUtils = mockStatic(ImageUtils.class)) {

            containerUtils.when(() -> ContainerUtils.removeContainer(eq(dockerClient), any())).thenAnswer(Answers.RETURNS_DEFAULTS);
            imageUtils.when(() -> ImageUtils.pullImages(dockerConfig, "newfish")).thenAnswer(Answers.RETURNS_DEFAULTS);

            LifecycleExecutor le = new LifecycleExecutor(config, extendedBuilder, origBuilder, PLATFORM_LEVEL);
            
            try{
                Field factory = ReflectionUtils.findFields(LifecycleExecutor.class, 
                                                        f->f.getName().equals("factory"), 
                                                        ReflectionUtils.HierarchyTraversalMode.TOP_DOWN)
                                            .get(0);
                factory.setAccessible(true);
                factory.set(le, lifecycleFactory);
            }catch(Exception e){
                fail();
            }


            le.execute();

            //check that we swapped to the new run image by reading the toml.
            assertEquals("newfish", runImages.get(0));

            //ensure correct phases driven in expected order for this platform revision.
            InOrder order = Mockito.inOrder(detector,analyzer,restorer,buildExtender,exporter);
            order.verify(analyzer).runPhase(logger, true);            
            order.verify(detector).runPhase(logger, true);
            order.verify(restorer).runPhase(logger, true);
            order.verify(buildExtender).runPhase(logger, true);
            order.verify(exporter).runPhase(logger, true);
        }   
    }

    @SuppressWarnings("resource")
    @Test
    void test12OnwardsXtns(
                  @Mock LifecyclePhaseFactory lifecycleFactory, 
                  @Mock BuilderImage extendedBuilder, 
                  @Mock BuilderImage origBuilder,
                  @Mock LogConfig logConfig,
                  @Mock CacheConfig buildCacheConfig,
                  @Mock CacheConfig launchCacheConfig,
                  @Mock CacheConfig kanikoCacheConfig,
                  @Mock PlatformConfig platformConfig,
                  @Mock DockerConfig dockerConfig, 
                  @Mock DockerClient dockerClient,
                  @Mock StartContainerCmd startCmd,
                  @Mock LogContainerCmd logCmd,
                  @Mock WaitContainerCmd waitCmd,
                  @Mock WaitContainerResultCallback waitResult,
                  @Mock Logger logger,
                  @Mock BuildConfig config,
                  @Mock Analyzer analyzer,
                  @Mock Builder builder,
                  @Mock Creator creator,
                  @Mock Detector detector,
                  @Mock Exporter exporter,
                  @Mock Extender runExtender,
                  @Mock Extender buildExtender,
                  @Mock Restorer restorer
    ){
        String PLATFORM_LEVEL="0.12";
        String OUTPUT_IMAGE="fish";
        boolean HAS_EXTENSIONS=true;

        List<String> runImages = new ArrayList<>();
        runImages.add("run-image");

        lenient().when(logConfig.getUseTimestamps()).thenReturn(true);
        lenient().when(logConfig.getLogger()).thenReturn(logger);

        lenient().when(dockerConfig.getPullPolicy()).thenReturn(DockerConfig.PullPolicy.IF_NOT_PRESENT);
        lenient().when(dockerConfig.getDockerClient()).thenReturn(dockerClient);
        lenient().when(dockerConfig.getPullTimeoutSeconds()).thenReturn(66);

        lenient().when(config.getDockerConfig()).thenReturn(dockerConfig);
        lenient().when(config.getBuildCacheConfig()).thenReturn(buildCacheConfig);
        lenient().when(config.getLaunchCacheConfig()).thenReturn(launchCacheConfig);
        lenient().when(config.getKanikoCacheConfig()).thenReturn(kanikoCacheConfig);
        lenient().when(config.getPlatformConfig()).thenReturn(platformConfig);
        lenient().when(config.getLogConfig()).thenReturn(logConfig);
        lenient().when(config.getOutputImage()).thenReturn(new ImageReference(OUTPUT_IMAGE));

        lenient().doNothing().when(lifecycleFactory).createVolumes(any());
        lenient().doNothing().when(lifecycleFactory).tidyUp();
        lenient().when(lifecycleFactory.getAnalyzer()).thenReturn(analyzer);
        lenient().when(lifecycleFactory.getDetector()).thenReturn(detector);
        lenient().when(lifecycleFactory.getBuilder()).thenReturn(builder);
        lenient().when(lifecycleFactory.getCreator()).thenReturn(creator);
        lenient().when(lifecycleFactory.getExporter(true)).thenReturn(exporter);
        lenient().when(lifecycleFactory.getRunImageExtender()).thenReturn(runExtender);
        lenient().when(lifecycleFactory.getBuildImageExtender()).thenReturn(buildExtender);
        lenient().when(lifecycleFactory.getRestorer()).thenReturn(restorer);
        lenient().when(lifecycleFactory.getBuilderImage()).thenReturn(extendedBuilder);
        lenient().when(lifecycleFactory.getPlatformLevel()).thenReturn(new Version(PLATFORM_LEVEL));
        lenient().when(lifecycleFactory.getDockerConfig()).thenReturn(dockerConfig);

        lenient().when(extendedBuilder.hasExtensions()).thenReturn(HAS_EXTENSIONS);
        lenient().when(extendedBuilder.getRunImages(any())).thenReturn(runImages);

        lenient().when(analyzer.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"analyzer-id"));
        lenient().when(detector.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"detector-id"));
        lenient().when(detector.getAnalyzedToml()).thenReturn("[run-image]\nreference = \"newfish\"\nextend = true".getBytes());
        lenient().when(builder.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"builder-id"));
        lenient().when(creator.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"creator-id"));
        lenient().when(exporter.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"exporter-id"));
        lenient().when(runExtender.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"run-extender-id"));
        lenient().when(buildExtender.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"build-extender-id"));
        lenient().when(restorer.runPhase(logger, true)).thenReturn(ContainerStatus.of(0,"restorer-id"));

        try (MockedStatic<? extends ContainerUtils> containerUtils = mockStatic(ContainerUtils.class);
            MockedStatic<? extends ImageUtils> imageUtils = mockStatic(ImageUtils.class)) {

            containerUtils.when(() -> ContainerUtils.removeContainer(eq(dockerClient), any())).thenAnswer(Answers.RETURNS_DEFAULTS);
            imageUtils.when(() -> ImageUtils.pullImages(dockerConfig, "newfish")).thenAnswer(Answers.RETURNS_DEFAULTS);

            LifecycleExecutor le = new LifecycleExecutor(config, extendedBuilder, origBuilder, PLATFORM_LEVEL);
            
            try{
                Field factory = ReflectionUtils.findFields(LifecycleExecutor.class, 
                                                        f->f.getName().equals("factory"), 
                                                        ReflectionUtils.HierarchyTraversalMode.TOP_DOWN)
                                            .get(0);
                factory.setAccessible(true);
                factory.set(le, lifecycleFactory);
            }catch(Exception e){
                fail();
            }


            le.execute();

            //check that we swapped to the new run image by reading the toml.
            assertEquals("newfish", runImages.get(0));

            //ensure correct phases driven in expected order for this platform revision.
            InOrder order = Mockito.inOrder(detector,analyzer,restorer,buildExtender,runExtender,exporter);
            order.verify(analyzer).runPhase(logger, true);            
            order.verify(detector).runPhase(logger, true);
            order.verify(restorer).runPhase(logger, true);
            order.verify(buildExtender).runPhase(logger, true);
            order.verify(runExtender).runPhase(logger, true);
            order.verify(exporter).runPhase(logger, true);
        }   
    }    
}
