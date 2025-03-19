package dev.snowdrop.buildpack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.snowdrop.buildpack.config.DockerConfig;
import dev.snowdrop.buildpack.config.ImageReference;
import dev.snowdrop.buildpack.config.PlatformConfig;
import dev.snowdrop.buildpack.docker.BuildContainerUtils;
import dev.snowdrop.buildpack.docker.ImageUtils;
import dev.snowdrop.buildpack.lifecycle.LifecycleExecutor;
import dev.snowdrop.buildpack.lifecycle.Version;
import dev.snowdrop.buildpack.utils.LifecycleMetadata;

public class BuildpackBuild {
    private static final Logger log = LoggerFactory.getLogger(BuildpackBuild.class);

    //platforms stored in order of preference.
    private final List<String> supportedPlatformLevels =Stream.of("0.12", "0.11", "0.10", "0.9", "0.8", "0.7", "0.6", "0.5", "0.4").collect(Collectors.toCollection(ArrayList::new));
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
            log.debug("Considering platforms "+platformsToConsider+" from builder image");
        }else{
            //using specified lifecycle, so use lifecycle platforms
            LifecycleMetadata lm = new LifecycleMetadata(dc, pc.getLifecycleImage());
            platformsToConsider = lm.getSupportedPlatformLevels();
            log.debug("Considering platforms "+platformsToConsider+" from lifecycle image");
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

        log.info("Buildpack build requested with config: \n"+
                 " - builder "+config.getBuilderImage().getCanonicalReference()+"\n"+
                 " - output "+config.getOutputImage().getReference()+"\n"+
                 " - logLevel "+config.getLogConfig().getLogLevel()+"\n"+
                 " - dockerHost "+config.getDockerConfig().getDockerHost()+"\n"+
                 " - dockerSocket "+config.getDockerConfig().getDockerSocket()+"\n"+
                 " - useDaemon "+config.getDockerConfig().getUseDaemon());

        log.info("Pulling Builder image");

        //obtain & pull & inspect Builder image.
        BuilderImage builder = new BuilderImage(config.getDockerConfig(), 
                                                config.getPlatformConfig(), 
                                                config.getRunImage(),
                                                config.getBuilderImage());

        //select active platform level.
        String activePlatformLevel = selectPlatformLevel(config.getDockerConfig(), 
                                                         config.getPlatformConfig(),
                                                         builder);

        //obtain run image list before extended builder is created.
        ImageReference runImages[] = builder.getRunImages(new Version(activePlatformLevel));                                                 
        //pull run images if needed.
        if(config.getDockerConfig().getUseDaemon()) {
            log.info("Pulling Run Image(s) (requesting architecture ["+builder.getImagePlatform()+"])");

            //precache the runimages listed in the orig builder before extending it.         
            ImageUtils.pullImages(config.getDockerConfig(), builder.getImagePlatform(), runImages);
        }


        log.debug("Creating Ephemeral Builder Image...");

        //create the extended builder image.
        BuilderImage extendedBuilder = BuildContainerUtils.createBuildImage(config.getDockerConfig().getDockerClient(),
                                                                            config.getPlatformConfig(),
                                                                            builder, 
                                                                  null, 
                                                                 null, 
                                                                 null);
        try{
            log.info("Initiating buildpack build with derived configuration: \n"+
                     " - ephemeralBuilder "+extendedBuilder.getImage().getCanonicalReference()+"\n"+
                     " - activePlatformLevel "+activePlatformLevel+"\n"+
                     " - build uid:gid "+extendedBuilder.getUserId()+":"+extendedBuilder.getGroupId()+"\n"+
                     " - withExtensions "+extendedBuilder.hasExtensions());
             
             // all platform pre-build tasks are now done, begin executing lifecycle phases as required
            LifecycleExecutor le = new LifecycleExecutor(config, builder, extendedBuilder, activePlatformLevel); 
            return le.execute();
        }finally{
            //clean up the extended builder image
            config.getDockerConfig().getDockerClient().removeImageCmd(extendedBuilder.getImage().getCanonicalReference()).exec(); 
        }
    }

    
}
