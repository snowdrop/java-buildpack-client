package dev.snowdrop.buildpack.docker;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CopyArchiveFromContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;

import dev.snowdrop.buildpack.BuilderImage;
import dev.snowdrop.buildpack.BuildpackException;
import dev.snowdrop.buildpack.config.ImageReference;
import dev.snowdrop.buildpack.config.PlatformConfig;
import dev.snowdrop.buildpack.lifecycle.LifecyclePhaseFactory;

//used to create ephemeral builder, streaming tarball content from one image to another.
public class BuildContainerUtils {

    private static final Logger log = LoggerFactory.getLogger(BuildContainerUtils.class);

    private static InputStream getArchiveStreamFromContainer(DockerClient dc, String containerId, String path){
        CopyArchiveFromContainerCmd copyLifecyleFromImageCmd = dc.copyArchiveFromContainerCmd(containerId, path);
        try{
          return copyLifecyleFromImageCmd.exec();
        }catch(NotFoundException nfe){
            throw BuildpackException.launderThrowable("Unable to locate container '"+containerId+"'", nfe);
        }
    }

    private static void putArchiveStreamToContainer(DockerClient dc, String containerId, String atPath, InputStream tarStream){
        dc.copyArchiveToContainerCmd(containerId).withTarInputStream(tarStream).withRemotePath(atPath).exec();
    }

    private static void processBuildModule(DockerClient dc, String targetContainerId, String moduleImageReference, String fromPath, String toPath){
        String containerId = null;
        List<String> command = Stream.of("").collect(Collectors.toList());
        try{
            containerId = ContainerUtils.createContainer(dc, moduleImageReference, command);
            InputStream stream = getArchiveStreamFromContainer(dc, containerId, fromPath);
            putArchiveStreamToContainer(dc, targetContainerId, toPath, stream);
        }finally{
            if(containerId!=null){
                ContainerUtils.removeContainer(dc, containerId);
            }
        }
    }

    private static void populateMountPointDirs(DockerClient dc, String targetContainerId, int uid, int gid, List<String> dirs){
        try (PipedInputStream in = new PipedInputStream(4096); PipedOutputStream out = new PipedOutputStream(in)) {
            AtomicReference<Exception> writerException = new AtomicReference<>();

            Runnable writer = new Runnable() {
                @Override
                public void run() {
                    try (TarArchiveOutputStream tout = new TarArchiveOutputStream(new GZIPOutputStream(new BufferedOutputStream(out)));) {
                        tout.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                        for (String dir : dirs) {
                            TarArchiveEntry tae = new TarArchiveEntry(dir + "/");
                            tae.setSize(0);
                            tae.setUserId(uid);
                            tae.setGroupId(gid);
                            tae.setMode(TarArchiveEntry.DEFAULT_DIR_MODE);
                            tout.putArchiveEntry(tae);
                            tout.closeArchiveEntry();
                        } 
                    } catch (Exception e) {
                        writerException.set(e);
                    }
                } 
            };

            Runnable reader = new Runnable() {
                @Override
                public void run() {
                dc.copyArchiveToContainerCmd(targetContainerId).withRemotePath("/").withTarInputStream(in).exec();
                }
            };

            Thread t1 = new Thread(writer);
            Thread t2 = new Thread(reader);
            t1.start();
            t2.start();
            try {
                t1.join();
                t2.join();
            } catch (InterruptedException ie) {
                throw BuildpackException.launderThrowable(ie);
            }

            // did the write thread complete without issues? if not, bubble the cause.
            Exception wio = writerException.get();
            if (wio != null) {
                throw BuildpackException.launderThrowable(wio);
            }
        } catch (IOException e) {
            throw BuildpackException.launderThrowable(e);
        }
    }

    private static void addDebug(DockerClient dc, PlatformConfig pc, String targetContainerId) {
        if(pc.getPhaseDebugScript()!=null || System.getenv("DEBUG_LIFECYCLE")!=null || System.getProperty("DEBUG_LIFECYCLE")!=null ) {
            String defaultDebugScript = "#!/bin/bash\n" +
                                        "echo \"DEBUG INFO\"\n" +
                                        "stat -c \"%A $a %u %g %n\" /*\n" + 
                                        "LC=$1\n" +
                                        "shift\n" +
                                        "$LC \"$@\"";

            String script = pc.getPhaseDebugScript()==null ? defaultDebugScript : pc.getPhaseDebugScript();
            
            ContainerUtils.addContentToContainer(dc, targetContainerId, "/cnb/lifecycle", 1002, 1000, "debug", 0755, script);

            //if we came in, we might be configured with a script, and not have the flag set yet.. set it, to ensure everything else can gate on just the flag.
            System.setProperty("DEBUG_LIFECYCLE","true");
        }
    }

    /**
     * Creates a build image using a builder image as the base, overlaying lifecycle/extensions/buildpack content 
     * from supplied images. 
     * 
     * @param dc          Dockerclient to use
     * @param baseBuilder ImageReference for builder imager to start with
     * @param lifecycle   ImageReference for lifecycle image to take lifecycle from
     * @param extensions  List of ImageReferences to take extensions from
     * @param buildpacks  List of ImageReferences to take buildpacks from
     * @return
     */
    public static BuilderImage createBuildImage(DockerClient dc, PlatformConfig pc, BuilderImage baseBuilder, ImageReference lifecycle, List<ImageReference> extensions, List<ImageReference> buildpacks) {

        log.debug("Creating Ephemeral image, from "+baseBuilder.getImage().getCanonicalReference()+" with uid:gid "+baseBuilder.getUserId()+":"+baseBuilder.getGroupId());

        List<String> command = Stream.of("").collect(Collectors.toList());
        String builderContainerId = ContainerUtils.createContainer(dc, baseBuilder.getImage().getCanonicalReference(), command);        

        try{
            if(lifecycle!=null)
                processBuildModule(dc, builderContainerId, lifecycle.getCanonicalReference(), "/cnb/lifecycle", "/cnb");

            if(extensions!=null)
                for(ImageReference extension: extensions)
                    processBuildModule(dc, builderContainerId, extension.getCanonicalReference(), "/cnb/extensions", "/cnb");
            
            if(buildpacks!=null)
                for(ImageReference buildpack: buildpacks)
                    processBuildModule(dc, builderContainerId, buildpack.getCanonicalReference(), "/cnb/buildpacks", "/cnb");

            populateMountPointDirs(dc, builderContainerId, baseBuilder.getUserId(), baseBuilder.getGroupId(), 
                                Stream.of(LifecyclePhaseFactory.KANIKO_VOL_PATH,
                                            LifecyclePhaseFactory.WORKSPACE_VOL_PATH,
                                            LifecyclePhaseFactory.LAYERS_VOL_PATH,
                                            LifecyclePhaseFactory.CACHE_VOL_PATH,
                                            LifecyclePhaseFactory.LAUNCH_CACHE_VOL_PATH,
                                            LifecyclePhaseFactory.PLATFORM_VOL_PATH,
                                            LifecyclePhaseFactory.PLATFORM_VOL_PATH+LifecyclePhaseFactory.ENV_PATH_PREFIX)
                                        .collect(Collectors.toList())); 
                                        
            addDebug(dc, pc, builderContainerId);

            String ephemeralBuilderImageId = ContainerUtils.commitContainer(dc, builderContainerId);

            String name = "buildpack-ephemeralbuilder-"+(new Random()).ints('a', 'z' + 1).limit(8)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();

            log.debug("Ephemeral Builder created with ID "+ephemeralBuilderImageId+" tagging with friendly name "+name+" for build ");

            dc.tagImageCmd(ephemeralBuilderImageId, "docker.io/"+name, "latest").exec();

            //commit the live container with the modifications and return it as the new Builder Image.
            return new BuilderImage(baseBuilder,
                                    (extensions!=null && !extensions.isEmpty()), 
                                    new ImageReference(name));
        }finally{
            if(builderContainerId!=null){
                ContainerUtils.removeContainer(dc, builderContainerId);
            }
        }
    }
}
