package dev.snowdrop.buildpack.phases;

import java.util.Arrays;
import java.util.Map;

import com.github.dockerjava.api.DockerClient;

import dev.snowdrop.buildpack.docker.ContainerUtils;
import dev.snowdrop.buildpack.docker.VolumeBind;



public class LifecyclePhaseFactory {

    //paths we use for mountpoints within build container.
    public final static String BUILD_VOL_PATH = "/bld";
    public final static String LAUNCH_VOL_PATH = "/launch";
    public final static String APP_VOL_PATH = "/app";
    public final static String OUTPUT_VOL_PATH = "/out";
    public final static String PLATFORM_VOL_PATH = "/platform";
    public final static String DOCKER_SOCKET_PATH = "/var/run/docker.sock";

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

    //volume mappings.
    final Map<String,String> volumePathsToVolumeNames;

    public LifecyclePhaseFactory( DockerClient dockerClient,
                                  String dockerSocket,
                                  String builderImageName,
                                  Integer buildUserId,
                                  Integer buildGroupId,
                                  String buildLogLevel,
                                  String runImageName,
                                  String finalImageName,
                                  Map<String,String> volumePathsToVolumeNames
                                ){
        this.dockerClient = dockerClient;
        this.dockerSocket = dockerSocket;

        this.buildUserId = buildUserId;
        this.buildGroupId = buildGroupId;
        this.buildLogLevel = buildLogLevel;

        this.builderImageName = builderImageName;        
        this.runImageName = runImageName;
        this.finalImageName = finalImageName;

        this.volumePathsToVolumeNames = volumePathsToVolumeNames;
    }

    String getContainerForPhase(String args[]){
        // create a container using builderImage that will invoke the creator process
        String id = ContainerUtils.createContainer(this.dockerClient, this.builderImageName, Arrays.asList(args),
        new VolumeBind(this.volumePathsToVolumeNames.get(LifecyclePhaseFactory.BUILD_VOL_PATH), LifecyclePhaseFactory.BUILD_VOL_PATH), 
        new VolumeBind(this.volumePathsToVolumeNames.get(LifecyclePhaseFactory.LAUNCH_VOL_PATH), LifecyclePhaseFactory.LAUNCH_VOL_PATH),
        new VolumeBind(this.volumePathsToVolumeNames.get(LifecyclePhaseFactory.APP_VOL_PATH), LifecyclePhaseFactory.APP_VOL_PATH), 
        new VolumeBind(this.volumePathsToVolumeNames.get(LifecyclePhaseFactory.DOCKER_SOCKET_PATH), LifecyclePhaseFactory.DOCKER_SOCKET_PATH),
        new VolumeBind(this.volumePathsToVolumeNames.get(LifecyclePhaseFactory.OUTPUT_VOL_PATH), LifecyclePhaseFactory.OUTPUT_VOL_PATH));
        return id;
    }

    public LifecyclePhase getCreator(){
        return new Creator(this);
    }
}
 
