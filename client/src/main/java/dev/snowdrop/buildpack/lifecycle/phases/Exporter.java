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


public class Exporter implements LifecyclePhase{

    private static final Logger log = LoggerFactory.getLogger(Exporter.class);

    final LifecyclePhaseFactory factory;
    final boolean runExtended;

    public Exporter( LifecyclePhaseFactory factory, boolean runExtended){
        this.factory = factory;
        this.runExtended = runExtended;
    }

    @Override
    public ContainerStatus runPhase(dev.snowdrop.buildpack.Logger logger, boolean useTimestamps) {

        //0.6 onwards..  added -process-type (to set default process type)
        //0.7 onwards..  removed -run-image
        //0.12 onwards.. -stack is removed and replaced with -run
        //               /cnb/stack.toml removed, replaced with /cnb/run.toml

        LifecycleArgs args = new LifecycleArgs("/cnb/lifecycle/exporter", factory.getOutputImage().getReference());

        args.addArg("-uid", "" + factory.getBuilderImage().getUserId());
        args.addArg("-gid", "" + factory.getBuilderImage().getGroupId());
        args.addArg("-app", LifecyclePhaseFactory.WORKSPACE_VOL_PATH + LifecyclePhaseFactory.APP_PATH_PREFIX);
        args.addArg("-layers", LifecyclePhaseFactory.LAYERS_VOL_PATH);
        args.addArg("-cache-dir", LifecyclePhaseFactory.CACHE_VOL_PATH);
        args.addArg("-launch-cache", LifecyclePhaseFactory.LAUNCH_CACHE_VOL_PATH);
        args.addArg("-log-level", factory.getLogConfig().getLogLevel());

        if(factory.getPlatformLevel().lessThan("0.7")){
            args.addArg("-run-image", factory.getBuilderImage().getRunImages(factory.getPlatformLevel()).get(0));
        }
        if(factory.getPlatformLevel().atLeast("0.12") && runExtended){
            args.addArg("-run", "/cnb/run.toml");
        }

        int runAsId = factory.getBuilderImage().getUserId();

        //if using daemon, add daemon arg, run as root
        if(factory.getDockerConfig().getUseDaemon()){
            args.addArg("-daemon");  
            runAsId = 0;
        }        


        String id = factory.getContainerForPhase(args.toArray(), runAsId);
        try{
            log.info("Export container id " + id+ " will be run with uid "+runAsId);
            log.debug("- container args "+args);        

            // launch the container!
            log.info("- launching export container");
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
            log.info("Export container complete, with exit code " + rc);    

            return ContainerStatus.of(rc,id);
        }catch(Exception e){
            if(id!=null){
                log.info("Exception during export, removing container "+id);
                ContainerUtils.removeContainer(factory.getDockerConfig().getDockerClient(), id);
                log.info("remove complete");
            }
            throw e;
        }
    }
    
}
