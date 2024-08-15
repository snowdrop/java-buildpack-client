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


public class Extender implements LifecyclePhase{

    private static final Logger log = LoggerFactory.getLogger(Extender.class);

    final LifecyclePhaseFactory factory;
    final String kind;

    public Extender( LifecyclePhaseFactory factory, String kind ){
        this.factory = factory;
        this.kind = kind;
    }

    @Override
    public ContainerStatus runPhase(dev.snowdrop.buildpack.Logger logger, boolean useTimestamps) {

        LifecycleArgs args = new LifecycleArgs("/cnb/lifecycle/extender", null);

        args.addArg("-uid", "" + factory.getBuilderImage().getUserId());
        args.addArg("-gid", "" + factory.getBuilderImage().getGroupId());
        args.addArg("-app", LifecyclePhaseFactory.WORKSPACE_VOL_PATH + LifecyclePhaseFactory.APP_PATH_PREFIX);
        args.addArg("-layers", LifecyclePhaseFactory.LAYERS_VOL_PATH);
        args.addArg("-platform", LifecyclePhaseFactory.PLATFORM_VOL_PATH);
        args.addArg("-log-level", factory.getLogConfig().getLogLevel());

        if(factory.getPlatformLevel().atLeast("0.12")){
            args.addArg("-kind", kind);
        }

        //extender process has to run as root.
        // 
        //as per https://buildpacks.io/docs/reference/spec/migration/platform-api-0.9-0.10/
        //... The extender user should have sufficient permissions to execute all RUN instructions, 
        //    typically it should run as root.
        int runAsId = 0;
        String id = factory.getContainerForPhase(args.toArray(), runAsId);
        try{
            log.info("Extender container id " + id+ " will be run with uid "+runAsId);   
            log.debug("- container args "+args);

            // launch the container!
            log.info("- launching extender container");
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
            log.info("Extender container complete, with exit code " + rc);    

            return ContainerStatus.of(rc,id);
        }catch(Exception e){
            if(id!=null){
                log.info("Exception during extender, removing container "+id);
                ContainerUtils.removeContainer(factory.getDockerConfig().getDockerClient(), id);
                log.info("remove complete");
            }
            throw e;
        }
    }
    
}
