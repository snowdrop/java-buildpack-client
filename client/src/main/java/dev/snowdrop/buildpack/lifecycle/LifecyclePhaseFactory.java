package dev.snowdrop.buildpack.lifecycle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.snowdrop.buildpack.BuilderImage;
import dev.snowdrop.buildpack.config.CacheConfig;
import dev.snowdrop.buildpack.config.DockerConfig;
import dev.snowdrop.buildpack.config.ImageReference;
import dev.snowdrop.buildpack.config.LogConfig;
import dev.snowdrop.buildpack.config.PlatformConfig;
import dev.snowdrop.buildpack.docker.ContainerEntry;
import dev.snowdrop.buildpack.docker.ContainerUtils;
import dev.snowdrop.buildpack.docker.Content;
import dev.snowdrop.buildpack.docker.StringContent;
import dev.snowdrop.buildpack.docker.VolumeBind;
import dev.snowdrop.buildpack.docker.VolumeUtils;
import dev.snowdrop.buildpack.lifecycle.phases.Analyzer;
import dev.snowdrop.buildpack.lifecycle.phases.Builder;
import dev.snowdrop.buildpack.lifecycle.phases.Creator;
import dev.snowdrop.buildpack.lifecycle.phases.Detector;
import dev.snowdrop.buildpack.lifecycle.phases.Exporter;
import dev.snowdrop.buildpack.lifecycle.phases.Extender;
import dev.snowdrop.buildpack.lifecycle.phases.Restorer;

public class LifecyclePhaseFactory {

    private static final Logger log = LoggerFactory.getLogger(LifecyclePhaseFactory.class);

    //paths we use for mountpoints within build container.
    public final static String CACHE_VOL_PATH = "/cache-dir";
    public final static String LAUNCH_CACHE_VOL_PATH = "/launch-cache-dir";
    public final static String WORKSPACE_VOL_PATH = "/workspace";
    public final static String LAYERS_VOL_PATH = "/layers";
    public final static String PLATFORM_VOL_PATH = "/platform";
    public final static String KANIKO_VOL_PATH = "/kaniko";
    public final static String DOCKER_SOCKET_PATH = "/var/run/docker.sock";

    public final static String APP_PATH_PREFIX = ""; //previously /content to avoid permissions, should not be issue with extended builder.
    public final static String ENV_PATH_PREFIX = "";

    private final DockerConfig     dockerConfig;
    private final CacheConfig      buildCacheConfig;
    private final CacheConfig      launchCacheConfig;
    private final CacheConfig      kanikoCacheConfig;
    private final PlatformConfig   platformConfig;
    private final LogConfig        logConfig;

    private final ImageReference   outputImage;
    private final BuilderImage     originalBuilder;
    private final BuilderImage     builder;
    private final Version          platformLevel;

    //names of the volumes during buildtime.
    final String buildCacheVolume;
    final String launchCacheVolume;
    final String kanikoCacheVolume;
    final String applicationVolume;
    final String outputVolume;
    final String platformVolume;

    // util method for random suffix.
    private String randomString(int length) {
        return (new Random()).ints('a', 'z' + 1).limit(length)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }  

    private String getVolumeName(CacheConfig config, String prefix) {
        String volumeName;
        if(config!=null && config.getCacheVolumeName() != null){
            volumeName = config.getCacheVolumeName();
        }else{
            volumeName = prefix + randomString(10);
        }
        return volumeName;
    }

    public String getContainerForPhase(String args[], Integer runAsId){
        ArrayList<VolumeBind> binds = new ArrayList<>(Arrays.asList(
                                        new VolumeBind(buildCacheVolume, LifecyclePhaseFactory.CACHE_VOL_PATH), 
                                        new VolumeBind(launchCacheVolume, LifecyclePhaseFactory.LAUNCH_CACHE_VOL_PATH),
                                        new VolumeBind(applicationVolume, LifecyclePhaseFactory.WORKSPACE_VOL_PATH),
                                        new VolumeBind(platformVolume, LifecyclePhaseFactory.PLATFORM_VOL_PATH),
                                        new VolumeBind(outputVolume, LifecyclePhaseFactory.LAYERS_VOL_PATH),
                                        new VolumeBind(kanikoCacheVolume, LifecyclePhaseFactory.KANIKO_VOL_PATH)
                                    ));        

        if(dockerConfig.getUseDaemon())
          binds.add(new VolumeBind(dockerConfig.getDockerSocket(), LifecyclePhaseFactory.DOCKER_SOCKET_PATH));

        // create a container using builderImage that will invoke the creator process
        String id = ContainerUtils.createContainer(dockerConfig.getDockerClient(), 
                                                   builder.getImage().getReference(), 
                                                   Arrays.asList(args), 
                                                   runAsId, 
                                                   platformConfig.getEnvironment(), 
                                                   "label=disable", 
                                                   dockerConfig.getDockerNetwork(),
                                                   binds);

        log.info("- mounted " + buildCacheVolume + " at " + CACHE_VOL_PATH);
        log.info("- mounted " + launchCacheVolume + " at " + LAUNCH_CACHE_VOL_PATH);
        log.info("- mounted " + kanikoCacheVolume + " at " + KANIKO_VOL_PATH);        
        log.info("- mounted " + applicationVolume + " at " + WORKSPACE_VOL_PATH);
        log.info("- mounted " + platformVolume + " at " + PLATFORM_VOL_PATH);
        if(dockerConfig.getUseDaemon())
          log.info("- mounted " + dockerConfig.getDockerSocket() + " at " + LifecyclePhaseFactory.DOCKER_SOCKET_PATH);
        log.info("- mounted " + outputVolume + " at " + LAYERS_VOL_PATH);
        log.info("- container id " + id);
        log.info("- image reference "+builder.getImage().getReference()); 
        return id;
    }

    public LifecyclePhaseFactory(DockerConfig dockerConfig,
                                 CacheConfig  buildCacheConfig,
                                 CacheConfig  launchCacheConfig,
                                 CacheConfig  kanikoCacheConfig,
                                 PlatformConfig platformConfig,
                                 LogConfig logConfig,
                                 ImageReference outputImage,
                                 BuilderImage originalBuilder,
                                 BuilderImage extendedBuilder,
                                 String platformLevel) {

        this.buildCacheVolume = getVolumeName(buildCacheConfig, "buildpack-build-");
        this.launchCacheVolume = getVolumeName(launchCacheConfig, "buildpack-launch-");
        this.applicationVolume = getVolumeName(null,"buildpack-app-");
        this.outputVolume = getVolumeName(null,"buildpack-output-");
        this.platformVolume = getVolumeName(null,"buildpack-platform-");
        this.kanikoCacheVolume = getVolumeName(kanikoCacheConfig,"buildpack-kaniko-");

        this.dockerConfig = dockerConfig;
        this.buildCacheConfig = buildCacheConfig;
        this.launchCacheConfig = launchCacheConfig;
        this.kanikoCacheConfig = kanikoCacheConfig;
        this.platformConfig = platformConfig;
        this.logConfig = logConfig;
        this.outputImage = outputImage;
        this.originalBuilder = originalBuilder;
        this.builder = extendedBuilder;
        this.platformLevel = new Version(platformLevel);
    }

    public void createVolumes(List<Content> content){
        // create the volumes.
        VolumeUtils.createVolumeIfRequired(dockerConfig.getDockerClient(), buildCacheVolume);
        VolumeUtils.createVolumeIfRequired(dockerConfig.getDockerClient(), launchCacheVolume);
        VolumeUtils.createVolumeIfRequired(dockerConfig.getDockerClient(), applicationVolume);
        VolumeUtils.createVolumeIfRequired(dockerConfig.getDockerClient(), outputVolume);
        VolumeUtils.createVolumeIfRequired(dockerConfig.getDockerClient(), platformVolume);
        VolumeUtils.createVolumeIfRequired(dockerConfig.getDockerClient(), kanikoCacheVolume);

        // add the application to the volume. Note we are placing it at /content,
        // because the volume mountpoint is mounted such that the user has no perms to create 
        // new content there, but subdirs are ok.
        log.info("There are "+content.size()+" entries to add for the app dir");
        List<ContainerEntry> appEntries = content
            .stream()
            .flatMap(c -> c.getContainerEntries().stream())
            .collect(Collectors.toList());

        log.info("Adding aplication to volume "+applicationVolume);
        VolumeUtils.addContentToVolume(dockerConfig.getDockerClient(), 
                                       applicationVolume,
                                       builder.getImage().getReference(), 
                                       LifecyclePhaseFactory.APP_PATH_PREFIX, 
                                       builder.getUserId(), 
                                       builder.getGroupId(), 
                                       appEntries);
  
        //add workarounds to environment.
        if(!platformConfig.getEnvironment().containsKey("CNB_PLATFORM_API")) platformConfig.getEnvironment().put("CNB_PLATFORM_API", platformLevel.toString());

        // This a workaround for a bug in older lifecyle revisions. https://github.com/buildpacks/lifecycle/issues/339        
        if(!platformConfig.getEnvironment().containsKey("CNB_REGISTRY_AUTH")) platformConfig.getEnvironment().put("CNB_REGISTRY_AUTH", "{}");

        //enable experimental features when required.
        if(builder.hasExtensions() && 
           platformLevel.atLeast("0.10") && 
           !platformConfig.getEnvironment().containsKey("CNB_EXPERIMENTAL_MODE")) {   
           platformConfig.getEnvironment().put("CNB_EXPERIMENTAL_MODE", "warn");
        }

        //add the environment entries to the platform volume.
        List<ContainerEntry> envEntries = platformConfig.getEnvironment().entrySet()
                                                     .stream()
                                                     .flatMap(e -> new StringContent("env/"+e.getKey(), 0777, e.getValue()).getContainerEntries().stream())
                                                     .collect(Collectors.toList());

        log.info("Adding platform entries to platform volume "+platformVolume);
        VolumeUtils.addContentToVolume(dockerConfig.getDockerClient(), 
                                       platformVolume,
                                       builder.getImage().getReference(),
                                       LifecyclePhaseFactory.ENV_PATH_PREFIX, 
                                       builder.getUserId(), 
                                       builder.getGroupId(),
                                       envEntries);  
    }

    public void tidyUp(){
        log.info("- tidying up the build volumes");
        // remove volumes
        // (note when/if we persist the cache between builds, we'll be more selective here over what we remove)
        if (buildCacheConfig.getDeleteCacheAfterBuild()) {
            VolumeUtils.removeVolume(dockerConfig.getDockerClient(), buildCacheVolume);
        }
        if (launchCacheConfig.getDeleteCacheAfterBuild()) {
            VolumeUtils.removeVolume(dockerConfig.getDockerClient(), launchCacheVolume);
        }
        if (kanikoCacheConfig.getDeleteCacheAfterBuild()) {
            VolumeUtils.removeVolume(dockerConfig.getDockerClient(), kanikoCacheVolume);
        }

        //always remove the app/output/platform vols, they are unique to each build.
        VolumeUtils.removeVolume(dockerConfig.getDockerClient(), applicationVolume);
        VolumeUtils.removeVolume(dockerConfig.getDockerClient(), outputVolume);
        VolumeUtils.removeVolume(dockerConfig.getDockerClient(), platformVolume);
    
        log.info("- build volumes tidied up");     
    }

    public BuilderImage getBuilderImage(){
        return builder;
    }
    public DockerConfig getDockerConfig(){
        return dockerConfig;
    }
    public LogConfig getLogConfig(){
        return logConfig;
    }
    public ImageReference getOutputImage(){
        return outputImage;
    }
    public Version getPlatformLevel(){
        return platformLevel;
    }

    public LifecyclePhase getCreator(){
        return new Creator(this);
    }
    public LifecyclePhase getAnalyzer(){
        return new Analyzer(this);
    }
    public LifecyclePhase getDetector(){
        return new Detector(this);
    }
    public LifecyclePhase getRestorer(){
        return new Restorer(this, originalBuilder);
    }
    public LifecyclePhase getBuilder(){
        return new Builder(this);
    }
    public LifecyclePhase getBuildImageExtender(){
        return getExtender("build");
    }
    public LifecyclePhase getRunImageExtender(){
        return getExtender("run");
    }    
    public LifecyclePhase getExtender(String kind){
        return new Extender(this, kind);
    }    
    public LifecyclePhase getExporter(boolean extended){
        return new Exporter(this, extended);
    }

}