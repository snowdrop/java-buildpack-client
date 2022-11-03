package dev.snowdrop.buildpack.phases;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;

import dev.snowdrop.buildpack.Buildpack;
import dev.snowdrop.buildpack.docker.ContainerEntry;
import dev.snowdrop.buildpack.docker.ContainerUtils;
import dev.snowdrop.buildpack.docker.Content;
import dev.snowdrop.buildpack.docker.StringContent;
import dev.snowdrop.buildpack.docker.VolumeBind;
import dev.snowdrop.buildpack.docker.VolumeUtils;



public class LifecyclePhaseFactory {

    private static final Logger log = LoggerFactory.getLogger(LifecyclePhaseFactory.class);

    //paths we use for mountpoints within build container.
    public final static String BUILD_VOL_PATH = "/bld";
    public final static String LAUNCH_VOL_PATH = "/launch";
    public final static String APP_VOL_PATH = "/app";
    public final static String OUTPUT_VOL_PATH = "/out";
    public final static String PLATFORM_VOL_PATH = "/platform";
    public final static String DOCKER_SOCKET_PATH = "/var/run/docker.sock";

    public final static String APP_PATH_PREFIX = "/content";

    //provided for this build by caller.
    final DockerClient dockerClient;
    final String dockerSocket;

    final Integer buildUserId;
    final Integer buildGroupId;
    final String buildLogLevel;

    //image names.
    final String builderImageName;    
    final String runImageName;
    final String finalImageName;

    //names of the volumes during runtime.
    final String buildCacheVolume;
    final String launchCacheVolume;
    final String applicationVolume;
    final String outputVolume;
    final String platformVolume;
    
    final List<Content> content;
    final Map<String,String> environment;

    final boolean removeBuildCacheAfterBuild;
    final boolean removeLaunchCacheAfterBuild;
    
    public LifecyclePhaseFactory( DockerClient dockerClient,
                                  Integer buildUserId,
                                  Integer buildGroupId,
                                  Buildpack buildConfig
                                ){
        this.dockerClient = dockerClient;
        this.dockerSocket = buildConfig.getDockerSocket();    

        this.buildUserId = buildUserId;
        this.buildGroupId = buildGroupId;
        this.buildLogLevel = buildConfig.getLogLevel();

        this.builderImageName = buildConfig.getBuilderImage();        
        this.runImageName = buildConfig.getRunImage();
        this.finalImageName = buildConfig.getFinalImage();

        this.content = buildConfig.getContent();
        this.environment = buildConfig.getEnvironment()==null ? new HashMap<>() : buildConfig.getEnvironment();

        this.removeBuildCacheAfterBuild = buildConfig.getRemoveBuildCacheAfterBuild();
        this.removeLaunchCacheAfterBuild = buildConfig.getRemoveLaunchCacheAfterBuild();

        this.buildCacheVolume = buildConfig.getBuildCacheVolumeName() == null ? "buildpack-build-" + randomString(10) : buildConfig.getBuildCacheVolumeName();
        this.launchCacheVolume = buildConfig.getLaunchCacheVolumeName() == null ? "buildpack-launch-" + randomString(10) : buildConfig.getLaunchCacheVolumeName();
        this.applicationVolume = "buildpack-app-" + randomString(10);
        this.outputVolume = "buildpack-output-" + randomString(10);
        this.platformVolume = "buildpack-platform-" + randomString(10);

        prep();
    }

    private void prep(){
        // create the volumes.
        VolumeUtils.createVolumeIfRequired(dockerClient, buildCacheVolume);
        VolumeUtils.createVolumeIfRequired(dockerClient, launchCacheVolume);
        VolumeUtils.createVolumeIfRequired(dockerClient, applicationVolume);
        VolumeUtils.createVolumeIfRequired(dockerClient, outputVolume);
        VolumeUtils.createVolumeIfRequired(dockerClient, platformVolume);

        // add the application to the volume. Note we are placing it at /content,
        // because the volume mountpoint is mounted such that the user has no perms to create 
        // new content there, but subdirs are ok.
        List<ContainerEntry> appEntries = content
            .stream()
            .flatMap(c -> c.getContainerEntries().stream())
            .collect(Collectors.toList());
        VolumeUtils.addContentToVolume(dockerClient, applicationVolume, LifecyclePhaseFactory.APP_PATH_PREFIX, buildUserId, buildGroupId, appEntries);

        //add the environment entries to the platform volume.
        List<ContainerEntry> envEntries = environment.entrySet()
            .stream()
            .flatMap(e -> new StringContent(e.getKey(), e.getValue()).getContainerEntries().stream())
            .collect(Collectors.toList());
        VolumeUtils.addContentToVolume(dockerClient, platformVolume, "/env", buildUserId, buildGroupId, envEntries);  
        
        //add workarounds to environment.
        if(!environment.containsKey("CNB_PLATFORM_API")) environment.put("CNB_PLATFORM_API", "0.4");
        // This a workaround for a bug in older lifecyle revisions. https://github.com/buildpacks/lifecycle/issues/339        
        if(!environment.containsKey("CNB_REGISTRY_AUTH")) environment.put("CNB_REGISTRY_AUTH", "{}");
    }

    // util method for random suffix.
    private String randomString(int length) {
        return (new Random()).ints('a', 'z' + 1).limit(length)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }    

    public void tidyUp(){
        // remove volumes
        // (note when/if we persist the cache between builds, we'll be more selective here over what we remove)
        if (removeBuildCacheAfterBuild) {
            VolumeUtils.removeVolume(dockerClient, buildCacheVolume);
        }
        if (removeLaunchCacheAfterBuild) {
            VolumeUtils.removeVolume(dockerClient, launchCacheVolume);
        }
        VolumeUtils.removeVolume(dockerClient, applicationVolume);
        VolumeUtils.removeVolume(dockerClient, outputVolume);
        VolumeUtils.removeVolume(dockerClient, platformVolume);
    
        log.info("- build volumes tidied up");     
    }


    String getContainerForPhase(String args[], Integer runAsId){
        // create a container using builderImage that will invoke the creator process
        String id = ContainerUtils.createContainer(this.dockerClient, this.builderImageName, Arrays.asList(args), 
        runAsId, environment, "label=disable",
        new VolumeBind(buildCacheVolume, LifecyclePhaseFactory.BUILD_VOL_PATH), 
        new VolumeBind(launchCacheVolume, LifecyclePhaseFactory.LAUNCH_VOL_PATH),
        new VolumeBind(applicationVolume, LifecyclePhaseFactory.APP_VOL_PATH), 
        new VolumeBind(platformVolume, LifecyclePhaseFactory.PLATFORM_VOL_PATH),
        new VolumeBind(dockerSocket, LifecyclePhaseFactory.DOCKER_SOCKET_PATH),
        new VolumeBind(outputVolume, LifecyclePhaseFactory.OUTPUT_VOL_PATH)
        );

        log.info("- mounted " + buildCacheVolume + " at " + BUILD_VOL_PATH);
        log.info("- mounted " + launchCacheVolume + " at " + LAUNCH_VOL_PATH);
        log.info("- mounted " + applicationVolume + " at " + APP_VOL_PATH);
        log.info("- mounted " + platformVolume + " at " + PLATFORM_VOL_PATH);
        log.info("- mounted " + dockerSocket + " at " + LifecyclePhaseFactory.DOCKER_SOCKET_PATH);
        log.info("- mounted " + outputVolume + " at " + OUTPUT_VOL_PATH);
        log.info("- build container id " + id);        

        return id;
    }

    public LifecyclePhase getCreator(){
        return new Creator(this);
    }
}
 
