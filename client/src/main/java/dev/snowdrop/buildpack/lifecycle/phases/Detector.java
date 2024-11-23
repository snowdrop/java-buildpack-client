package dev.snowdrop.buildpack.lifecycle.phases;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.command.WaitContainerResultCallback;

import dev.snowdrop.buildpack.ContainerLogReader;
import dev.snowdrop.buildpack.docker.ContainerUtils;
import dev.snowdrop.buildpack.docker.StringContent;
import dev.snowdrop.buildpack.lifecycle.ContainerStatus;

import dev.snowdrop.buildpack.lifecycle.LifecyclePhase;
import dev.snowdrop.buildpack.lifecycle.LifecyclePhaseAnalyzedTomlUpdater;
import dev.snowdrop.buildpack.lifecycle.LifecyclePhaseFactory;
import dev.snowdrop.buildpack.utils.LifecycleArgs;


public class Detector implements LifecyclePhase, LifecyclePhaseAnalyzedTomlUpdater{

    private static final Logger log = LoggerFactory.getLogger(Detector.class);

    private final LifecyclePhaseFactory factory;

    private byte[] analyzedToml = null;

    public Detector( LifecyclePhaseFactory factory ){
        this.factory = factory;
    }

    @Override
    public ContainerStatus runPhase(dev.snowdrop.buildpack.Logger logger, boolean useTimestamps) {

        //0.7 onwards.. detector will look for order.toml in /layers before checking other paths. 
        //              allowing platforms to write an order.toml & override the builders order.toml
        
        //0.10 onwards.. when processing an extensions build, add -generated (default: layers/generated)

        //0.12 onwards.. when run Images are present, invoke with -run to specify /cnb/run.toml 
        //               (creates warning if switched image is not in set).

        LifecycleArgs args = new LifecycleArgs("/cnb/lifecycle/detector", null);

        args.addArg("-app", LifecyclePhaseFactory.WORKSPACE_VOL_PATH + LifecyclePhaseFactory.APP_PATH_PREFIX);
        args.addArg("-analyzed", LifecyclePhaseFactory.LAYERS_VOL_PATH + "/analyzed.toml");        
        args.addArg("-layers", LifecyclePhaseFactory.LAYERS_VOL_PATH);
        args.addArg("-platform", LifecyclePhaseFactory.PLATFORM_VOL_PATH);
        args.addArg("-log-level", factory.getLogConfig().getLogLevel());

        if(factory.getPlatformLevel().atLeast("0.10") && factory.getBuilderImage().hasExtensions() ){
            args.addArg("-generated", LifecyclePhaseFactory.LAYERS_VOL_PATH + "/generated");
        }

        if(factory.getPlatformLevel().atLeast("0.12") && factory.getBuilderImage().hasExtensions() ){
            args.addArg("-run", "/cnb/run.toml");
        }

        // detector phase must run as non-root
        int runAsId = factory.getBuilderImage().getUserId();
        String id = factory.getContainerForPhase(args.toArray(), runAsId);
        try{
            log.info("Detect container id " + id+ " will be run with uid "+runAsId);
            log.debug("- container args "+args);                         

            // launch the container!
            log.info("- launching detect container");
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
            log.info("Detect container complete, with exit code " + rc);   
            
            analyzedToml = ContainerUtils.getFileFromContainer(factory.getDockerConfig().getDockerClient(), 
                                                            id, 
                                                            LifecyclePhaseFactory.LAYERS_VOL_PATH + "/analyzed.toml");

            return ContainerStatus.of(rc,id);
        }catch(Exception e){
            if(id!=null){
                log.info("Exception during detect, removing container "+id);
                ContainerUtils.removeContainer(factory.getDockerConfig().getDockerClient(), id);
                log.info("remove complete");
            }
            throw e;
        }
    }

    public byte[] getAnalyzedToml(){
        return analyzedToml;
    }

    public void updateAnalyzedToml(String toml){
        factory.addContentToLayersVolume(new StringContent("analyzed.toml", 0777, toml));
    }
    
}
