package dev.snowdrop.buildpack.lifecycle.phases;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.command.WaitContainerResultCallback;

import dev.snowdrop.buildpack.ContainerLogReader;
import dev.snowdrop.buildpack.docker.ContainerUtils;
import dev.snowdrop.buildpack.lifecycle.ContainerStatus;
import dev.snowdrop.buildpack.lifecycle.LifecyclePhase;
import dev.snowdrop.buildpack.lifecycle.LifecyclePhaseFactory;
import dev.snowdrop.buildpack.utils.LifecycleArgs;


public class Creator implements LifecyclePhase{

    private static final Logger log = LoggerFactory.getLogger(Creator.class);

    final LifecyclePhaseFactory factory;

    public Creator( LifecyclePhaseFactory factory ){
        this.factory = factory;
    }

    @Override
    public ContainerStatus runPhase(dev.snowdrop.buildpack.Logger logger, boolean useTimestamps) {

        LifecycleArgs args = new LifecycleArgs("/cnb/lifecycle/creator", factory.getOutputImage().getReferenceWithLatest());

        args.addArg("-uid", "" + factory.getBuilderImage().getUserId());
        args.addArg("-gid", "" + factory.getBuilderImage().getGroupId());
        args.addArg("-cache-dir", LifecyclePhaseFactory.CACHE_VOL_PATH);
        args.addArg("-app", LifecyclePhaseFactory.WORKSPACE_VOL_PATH + LifecyclePhaseFactory.APP_PATH_PREFIX);
        args.addArg("-layers", LifecyclePhaseFactory.LAYERS_VOL_PATH);
        args.addArg("-platform", LifecyclePhaseFactory.PLATFORM_VOL_PATH);
        args.addArg("-run-image", factory.getBuilderImage().getRunImages(factory.getPlatformLevel())[0].getReferenceWithLatest());
        args.addArg("-log-level", factory.getLogConfig().getLogLevel());

        if(factory.getPlatformLevel().atLeast("0.9") && factory.getDockerConfig().getUseDaemon()){
            args.addArg("-launch-cache", LifecyclePhaseFactory.LAUNCH_CACHE_VOL_PATH);
        }

        //if using daemon, add daemon arg
        if(factory.getDockerConfig().getUseDaemon()){
            args.addArg("-daemon");  
        }
    
        //creator process always has to run as root.
        int runAsId = 0;
        String id = factory.getContainerForPhase(args.toArray(), runAsId);
        try{
            log.info("Creator container id " + id+ " will be run with uid "+runAsId);
            log.debug("- container args "+args);

            // launch the container!
            log.info("- launching build container");
            factory.getDockerConfig().getDockerClient().startContainerCmd(id).exec();            

            log.info("- attaching log relay");
            // grab the logs to stdout.
            factory.getDockerConfig().getDockerClient().logContainerCmd(id)
                .withFollowStream(true)
                .withStdOut(true)
                .withStdErr(true)
                .withTimestamps(useTimestamps)
                .exec(new ContainerLogReader(logger));

            // wait for the container to complete, and retrieve the exit code.
            int rc = factory.getDockerConfig().getDockerClient().waitContainerCmd(id).exec(new WaitContainerResultCallback()).awaitStatusCode();
            log.info("Creator container complete, with exit code " + rc);    

            return ContainerStatus.of(rc,id);
        }catch(Exception e){
            if(id!=null){
                log.info("Exception during creator, removing container "+id);
                ContainerUtils.removeContainer(factory.getDockerConfig().getDockerClient(), id);
                log.info("remove complete");
            }
            throw e;
        }
    }
    
}
