package dev.snowdrop.buildpack.lifecycle.phases;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.command.WaitContainerResultCallback;

import dev.snowdrop.buildpack.BuilderImage;
import dev.snowdrop.buildpack.ContainerLogReader;
import dev.snowdrop.buildpack.docker.ContainerUtils;
import dev.snowdrop.buildpack.lifecycle.ContainerStatus;
import dev.snowdrop.buildpack.lifecycle.LifecyclePhase;
import dev.snowdrop.buildpack.lifecycle.LifecyclePhaseFactory;
import dev.snowdrop.buildpack.utils.LifecycleArgs;


public class Restorer implements LifecyclePhase{

    private static final Logger log = LoggerFactory.getLogger(Restorer.class);

    final LifecyclePhaseFactory factory;
    final BuilderImage originalBuilder;

    public Restorer( LifecyclePhaseFactory factory , BuilderImage originalBuilder){
        this.factory = factory;
        this.originalBuilder = originalBuilder;
    }

    @Override
    public ContainerStatus runPhase(dev.snowdrop.buildpack.Logger logger, boolean useTimestamps) {

        //0.7 onwards..  added -analyzed, -skip-layers
        //0.10 onwards.. when building with extensions, add -build-image flag
        //0.12 onwards.. if in daemon mode, use new -daemon flag for restorer

        LifecycleArgs args = new LifecycleArgs("/cnb/lifecycle/restorer", null); 

        args.addArg("-uid", "" + factory.getBuilderImage().getUserId());
        args.addArg("-gid", "" + factory.getBuilderImage().getGroupId());
        args.addArg("-layers", LifecyclePhaseFactory.LAYERS_VOL_PATH);
        args.addArg("-cache-dir", LifecyclePhaseFactory.CACHE_VOL_PATH);        
        args.addArg("-log-level", factory.getLogConfig().getLogLevel());

        if(factory.getPlatformLevel().atLeast("0.10") && factory.getBuilderImage().hasExtensions()){
            //Spec unclear, setting this should be allowed, but causes errors
            //Not an issue, because kaniko dir MUST always be /kaniko (lpf.KANIKO_VOL_PATH)
            //args.addArg("-kaniko-dir", LifecyclePhaseFactory.KANIKO_VOL_PATH);
            
            //Can't use ephemeral/extended builder here as not in registry and restore tries to pull image..
            //Use original configured builder instead.. 
            args.addArg("-build-image", originalBuilder.getImage().getReference());
        }

        if(factory.getPlatformLevel().atLeast("0.12") && factory.getDockerConfig().getUseDaemon()){
            args.addArg("-daemon");
        }

        int runAsId = factory.getBuilderImage().getUserId();
        String id = factory.getContainerForPhase(args.toArray(), runAsId);
        try{
            log.info("- restorer container id " + id+ " will be run with uid "+runAsId+" and args "+args);
            System.out.println("- restorer container id " + id+ " will be run with uid "+runAsId+" and args "+args);

            // launch the container!
            log.info("- launching restorer container");
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
            log.info("Buildpack restorer container complete, with exit code " + rc);    

            return ContainerStatus.of(rc,id);
        }catch(Exception e){
            if(id!=null){
                log.info("Exception during restorer, removing container "+id);
                ContainerUtils.removeContainer(factory.getDockerConfig().getDockerClient(), id);
                log.info("remove complete");
            }
            throw e;
        }
    }
    
}
