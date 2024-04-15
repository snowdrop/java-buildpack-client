package dev.snowdrop.buildpack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.snowdrop.buildpack.config.DockerConfig;
import dev.snowdrop.buildpack.config.PlatformConfig;
import dev.snowdrop.buildpack.docker.BuildContainerUtils;
import dev.snowdrop.buildpack.lifecycle.LifecycleExecutor;
import dev.snowdrop.buildpack.lifecycle.Version;
import dev.snowdrop.buildpack.utils.LifecycleMetadata;

public class BuildpackBuild {

    //platforms stored in order of preference.
    private final List<String> supportedPlatformLevels =Stream.of("0.12", "0.11", "0.10", "0.9", "0.8", "0.7", "0.6", "0.5", "0.4").collect(Collectors.toList());
    //default platform level, used if not overridden
    public final String DEFAULT_PLATFORM_LEVEL = supportedPlatformLevels.get(0);

    BuildConfig config;

    public BuildpackBuild(BuildConfig config){
        this.config = config;
    }

    private String selectPlatformLevel(DockerConfig dc, PlatformConfig pc, BuilderImage builder) {
        List<String> platformsToConsider;

        if(pc.getLifecycleImage() == null){
            //using lifecycle from builder, so use builder platforms
            platformsToConsider = builder.getBuilderSupportedPlatforms();
        }else{
            //using specified lifecycle, so use lifecycle platforms
            LifecycleMetadata lm = new LifecycleMetadata(dc, pc.getLifecycleImage());
            platformsToConsider = lm.getSupportedPlatformLevels();
        }

        //if platform config requests specific platform, filter to only that platform level
        List<String> platformsToTest = new ArrayList<>(supportedPlatformLevels);
        if(pc.getPlatformLevel()!=null && supportedPlatformLevels.contains(pc.getPlatformLevel())){
            platformsToTest.clear();
            platformsToTest.add(pc.getPlatformLevel());
        }

        //find & return first match.
        for(String platform: platformsToTest){
            if(platformsToConsider.contains(platform)){
                return platform;
            }
        }
        
        //no match? appropriate exception.
        if(pc.getLifecycleImage()!=null){
            throw new BuildpackException("Unable to determine compatible platform for supplied lifecycle image", new IllegalStateException());
        }else{
            throw new BuildpackException("Unable to determine compatible platform for builder lifecycle image", new IllegalStateException());
        }

    }

    public int build(){

        //obtain & pull & inspect Builder image.
        BuilderImage builder = new BuilderImage(config.getDockerConfig(), 
                                                config.getPlatformConfig(), 
                                                config.getRunImage(),
                                                config.getBuilderImage());

        //select active platform level.
        String activePlatformLevel = selectPlatformLevel(config.getDockerConfig(), 
                                                         config.getPlatformConfig(),
                                                         builder);

        //precache the runimages in the orig builder before extending it.
        builder.getRunImages(new Version(activePlatformLevel));
        //create the extended builder image.
        BuilderImage extendedBuilder = BuildContainerUtils.createBuildImage(config.getDockerConfig().getDockerClient(), 
                                                                            builder, 
                                                                  null, 
                                                                 null, 
                                                                 null);
        try{
             // execute the build using the lifecycle.
            LifecycleExecutor le = new LifecycleExecutor(config, builder, extendedBuilder, activePlatformLevel); 
            return le.execute();
        }finally{
            //clean up the extended builder image
            config.getDockerConfig().getDockerClient().removeImageCmd(extendedBuilder.getImage().getReference()).exec(); 
        }
    }

    
}
