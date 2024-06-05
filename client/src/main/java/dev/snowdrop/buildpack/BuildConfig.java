package dev.snowdrop.buildpack;

import java.util.ArrayList;
import java.util.List;

import dev.snowdrop.buildpack.config.CacheConfig;
import dev.snowdrop.buildpack.config.DockerConfig;
import dev.snowdrop.buildpack.config.ImageReference;
import dev.snowdrop.buildpack.config.LogConfig;
import dev.snowdrop.buildpack.config.PlatformConfig;
import dev.snowdrop.buildpack.docker.Content;
import io.sundr.builder.annotations.Buildable;


@Buildable(generateBuilderPackage=true, builderPackage="dev.snowdrop.buildpack.builder")
public class BuildConfig {
    public static BuildConfigBuilder builder() {
        return new BuildConfigBuilder();
    }

    private static final ImageReference DEFAULT_BUILDER_IMAGE = new ImageReference("paketobuildpacks/builder:base");

    private DockerConfig dockerConfig;
    private CacheConfig buildCacheConfig;
    private CacheConfig launchCacheConfig;
    private CacheConfig kanikoCacheConfig;
    private PlatformConfig platformConfig;
    private LogConfig logConfig;
    private ImageReference builderImage;
    private ImageReference runImage;
    private ImageReference outputImage;
    private List<Content> application;

    private final int exitCode;

    public BuildConfig(DockerConfig dockerConfig,
                       CacheConfig  buildCacheConfig,
                       CacheConfig  launchCacheConfig,
                       CacheConfig  kanikoCacheConfig,
                       PlatformConfig platformConfig,
                       LogConfig logConfig,
                       ImageReference builderImage,
                       ImageReference runImage,
                       ImageReference outputImage,
                       List<Content> application){
        this.dockerConfig = dockerConfig != null ? dockerConfig : DockerConfig.builder().build();
        this.buildCacheConfig = buildCacheConfig != null ? buildCacheConfig : CacheConfig.builder().build();
        this.launchCacheConfig = launchCacheConfig != null ? launchCacheConfig : CacheConfig.builder().build();
        this.kanikoCacheConfig = kanikoCacheConfig != null ? kanikoCacheConfig : CacheConfig.builder().build();
        this.platformConfig = platformConfig != null ? platformConfig : PlatformConfig.builder().build();
        this.logConfig = logConfig != null ? logConfig : LogConfig.builder().build();
        this.builderImage = builderImage != null ? builderImage : DEFAULT_BUILDER_IMAGE;
        this.runImage = runImage;
        this.outputImage = outputImage;
        this.application = application != null ? application : new ArrayList<>();

        if(this.outputImage==null){
            throw new BuildpackException("Output Image missing and must be specified", new IllegalArgumentException());
        }
        if(this.application.size()==0){
            throw new BuildpackException("Application content missing and must be specified", new IllegalArgumentException());
        }

        exitCode = new BuildpackBuild(this).build();
    }

    public DockerConfig getDockerConfig(){
        return dockerConfig;
    }
    public CacheConfig getBuildCacheConfig(){
        return buildCacheConfig;
    }
    public CacheConfig getLaunchCacheConfig(){
        return launchCacheConfig;
    }
    public CacheConfig getKanikoCacheConfig(){
        return kanikoCacheConfig;
    }
    public PlatformConfig getPlatformConfig(){
        return platformConfig;
    }
    public LogConfig getLogConfig(){
        return logConfig;
    }
    public ImageReference getBuilderImage(){
        return builderImage;
    }
    public ImageReference getRunImage(){
        return runImage;
    }
    public ImageReference getOutputImage(){
        return outputImage;
    }
    public List<Content> getApplication(){
        return application;
    }
    public int getExitCode() {
        return this.exitCode;
    }
}


