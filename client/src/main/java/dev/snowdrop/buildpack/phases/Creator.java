package dev.snowdrop.buildpack.phases;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import dev.snowdrop.buildpack.ContainerLogReader;


public class Creator implements LifecyclePhase{

    private static final Logger log = LoggerFactory.getLogger(Creator.class);

    final LifecyclePhaseFactory factory;

    public Creator( LifecyclePhaseFactory factory ){
        this.factory = factory;
    }

    @Override
    public ContainerStatus runPhase(dev.snowdrop.buildpack.Logger logger, boolean useTimestamps) {

        // configure our call to 'creator' which will do all the work.
        String[] args = { "/cnb/lifecycle/creator", 
                        "-uid", "" + factory.buildUserId, 
                        "-gid", "" + factory.buildGroupId, 
                        "-cache-dir", factory.buildCachePath,
                        "-app", LifecyclePhaseFactory.APP_VOL_PATH + LifecyclePhaseFactory.APP_PATH_PREFIX, 
                        "-layers", LifecyclePhaseFactory.OUTPUT_VOL_PATH, 
                        "-platform", LifecyclePhaseFactory.PLATFORM_VOL_PATH, 
                        "-run-image", factory.runImageName, 
                        "-log-level", factory.buildLogLevel, 
                        "-skip-restore", 
                        factory.finalImageName };

        //if using daemon, inject daemon arg before final image name.
        if(factory.useDaemon){
            int len = args.length;
            args = Arrays.copyOf(args, len+3);

            //copy the image name out to the end of the list.
            args[len+2] = args[len-1];

            //insert the daemon args before eol.
            args[len-1] = "-daemon";
            args[len-0] = "-launch-cache";
            args[len+1] = LifecyclePhaseFactory.LAUNCH_VOL_PATH;
            
        }

        // TODO: add labels for container for creator etc (as per spec)
    
        //creator process always has to run as root.
        String id = factory.getContainerForPhase(args, 0);
        log.info("- build container id " + id);

        // launch the container!
        log.info("- launching build container");
        factory.dockerClient.startContainerCmd(id).exec();

        log.info("- attaching log relay");
        // grab the logs to stdout.
        factory.dockerClient.logContainerCmd(id)
        .withFollowStream(true)
        .withStdOut(true)
        .withStdErr(true)
        .withTimestamps(useTimestamps)
        .exec(new ContainerLogReader(logger));

        // wait for the container to complete, and retrieve the exit code.
        int rc = factory.dockerClient.waitContainerCmd(id).exec(new WaitContainerResultCallback()).awaitStatusCode();
        log.info("Buildpack container complete, with exit code " + rc);    

        return ContainerStatus.of(rc,id);
    }
    
}
