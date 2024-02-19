package dev.snowdrop.buildpack.lifecycle.phases;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.command.WaitContainerResultCallback;

import dev.snowdrop.buildpack.ContainerLogReader;
import dev.snowdrop.buildpack.lifecycle.ContainerStatus;
import dev.snowdrop.buildpack.lifecycle.LifecyclePhase;
import dev.snowdrop.buildpack.lifecycle.LifecyclePhaseFactory;
import dev.snowdrop.buildpack.utils.LifecycleArgs;


public class Analyzer implements LifecyclePhase{

    private static final Logger log = LoggerFactory.getLogger(Analyzer.class);

    final LifecyclePhaseFactory factory;

    public Analyzer( LifecyclePhaseFactory factory ){
        this.factory = factory;
    }

    @Override
    public ContainerStatus runPhase(dev.snowdrop.buildpack.Logger logger, boolean useTimestamps) {

        LifecycleArgs args = new LifecycleArgs("/cnb/lifecycle/analyzer", factory.getOutputImage().getReference());

        // 0.7 onwards.. removed 
        //                 -cache-dir, Path to cach directory
        //                 -group, Path to group definition (group.toml)
        //                 -skip-layers, Do not perform layer analysis
        //              added 
        //                 -previous-image, Image reference to be analyzed (usually the result of the previous build)
        //                 -run-image, Run image reference
        //                 -stack, Path to stack.toml
        //                 -tag, additional tag to apply to exported image


        // 0.9 onwards.. added
        //                 -launch-cache flag, used when restoring SBOM layer from prev image in daemon.
        //                 -skip-layers flag .. skips SBOM restoration entirely

        // 0.12 onwards..  -stack is removed and replaced with -run

        args.addArg("-uid", "" + factory.getBuilderImage().getUserId());
        args.addArg("-gid", "" + factory.getBuilderImage().getGroupId());
        args.addArg("-run-image", factory.getBuilderImage().getRunImages(factory.getPlatformLevel()).get(0));
        args.addArg("-log-level", factory.getLogConfig().getLogLevel());
        args.addArg("-analyzed", LifecyclePhaseFactory.LAYERS_VOL_PATH + "/analyzed.toml");

        if(factory.getPlatformLevel().atLeast("0.9") && factory.getDockerConfig().getUseDaemon()){
            args.addArg("-launch-cache", LifecyclePhaseFactory.LAUNCH_CACHE_VOL_PATH);
        }

        //if using daemon, add daemon arg
        if(factory.getDockerConfig().getUseDaemon()){
            args.addArg("-daemon");  
        }
    
        int runAsId = factory.getBuilderImage().getUserId();
        String id = factory.getContainerForPhase(args.toArray(), runAsId);
        log.info("- analyze container id " + id+ " will be run with uid "+runAsId);

        // launch the container!
        log.info("- launching analyze container");
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
        log.info("Buildpack analyze container complete, with exit code " + rc);    

        return ContainerStatus.of(rc,id);
    }


    
}
