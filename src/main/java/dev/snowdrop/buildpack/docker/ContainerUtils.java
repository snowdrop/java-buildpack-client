package dev.snowdrop.buildpack.docker;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;

import org.apache.commons.io.IOUtils;

import dev.snowdrop.buildpack.docker.ContainerEntry.ContentSupplier;

public class ContainerUtils {
  private static final Logger log = LoggerFactory.getLogger(ContainerUtils.class);


  public static String createContainer(DockerClient dc, String imageReference, VolumeBind... volumes) {
    return createContainer(dc, imageReference, null, volumes);
  }

  private static Bind createBind(VolumeBind vb) {
    return new Bind(vb.volumeName, new Volume(vb.mountPath));
  }

  public static String createContainer(DockerClient dc, String imageReference, List<String> command,
      VolumeBind... volumes) {

    CreateContainerCmd ccc = dc.createContainerCmd(imageReference);
    if (volumes != null) {
      List<Bind> binds = new ArrayList<>();
      for (VolumeBind vb : volumes) {
        Bind bind = createBind(vb);
        binds.add(bind);
      }
      ccc.getHostConfig().withBinds(binds);
    }

    // tbc, I think the buildpack container expects to run as root. At least it
    // seems to execute things that require root
    // eg, chmodding a mountpoint owned by root.
    ccc.withUser("root");

    // TODO: this a workaround for a bug in current buildpack
    // https://github.com/buildpacks/lifecycle/issues/339
    ccc.withEnv("CNB_PLATFORM_API=0.4", "CNB_REGISTRY_AUTH={}");

    if (command != null) {
      ccc.withCmd(command);
    }
    CreateContainerResponse ccr = ccc.exec();
    return ccr.getId();
  }

  public static void removeContainer(DockerClient dc, String containerId) {
    dc.removeContainerCmd(containerId).exec();
  }

  public static void addContentToContainer(DockerClient dc, String containerId, ContainerEntry... entries)
      throws IOException {
    addContentToContainer(dc, containerId, "", 0, 0, entries);
  }

  public static void addContentToContainer(DockerClient dc, String containerId, String pathInContainer, Integer userId,
      Integer groupId, File content) throws IOException {
    addContentToContainer(dc, containerId, pathInContainer, userId, groupId, ContainerEntry.fromFile("", content));
  }

  public static void addContentToContainer(DockerClient dc, String containerId, String pathInContainer, Integer userId,
      Integer groupId, String name, String content) throws IOException {
    addContentToContainer(dc, containerId, pathInContainer, userId, groupId, ContainerEntry.fromString(name, content));
  }

  /**
   * util method to add the parent dirs of a file/dir to an archive, to force them
   * to exist with the correct permissions, otherwise implicitly defined
   * directories were being created with perms preventing the buildpack from
   * executing as expected.
   */
  private static void addParents(TarArchiveOutputStream tout, Set<String> seenDirs, int uid, int gid, String path)
      throws IOException {
    if (path.contains("/")) {
      String parent = path.substring(0, path.lastIndexOf("/"));
      boolean unknown = seenDirs.add(parent);
      // only need to follow this chain if we haven't done it already =)
      if (unknown) {
        // add parents of this FIRST
        addParents(tout, seenDirs, uid, gid, parent);

        log.debug("adding "+parent+"/");
        // and then add this =)
        TarArchiveEntry tae = new TarArchiveEntry(parent + "/");
        tae.setSize(0);
        tae.setUserId(uid);
        tae.setGroupId(gid);
        tout.putArchiveEntry(tae);
        tout.closeArchiveEntry();
      }
    }
  }

  /**
   * Adds content to the container, with specified uid/gid
   */
  public static void addContentToContainer(DockerClient dc, String containerId, String pathInContainer, Integer userId,
      Integer groupId, ContainerEntry... entries) throws IOException {

    Set<String> seenDirs = new HashSet<>();
    // Don't add entry for "/", causes issues with tar format.
    seenDirs.add("");

    // use supplied pathInContainer, trim off trailing "/" where required.
    final String path = (!pathInContainer.isEmpty() && pathInContainer.endsWith("/"))
        ? pathInContainer.substring(0, pathInContainer.length() - 1)
        : pathInContainer;
    // set uid/gid to the supplied values, or 0 if not supplied.
    final int uid = (userId != null) ? userId : 0;
    final int gid = (groupId != null) ? groupId : 0;

    try (PipedInputStream in = new PipedInputStream(4096); PipedOutputStream out = new PipedOutputStream(in)) {
      AtomicReference<IOException> writerException = new AtomicReference<>();

      Runnable writer = new Runnable() {
        @Override
        public void run() {
          try (TarArchiveOutputStream tout = new TarArchiveOutputStream(
              new GZIPOutputStream(new BufferedOutputStream(out)));) {
            tout.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            for (ContainerEntry ve : entries) {
              // prefix the entry path with the pathInContainer value.
              String entryPath = ve.getPath();
              
              if(entryPath==null || entryPath.isEmpty()) {
                throw new IOException("Error path was empty");
              }
              
              if (entryPath.startsWith("/"))
                entryPath = entryPath.substring(1);
              String pathWithEntry = path + "/" + entryPath;

              // important! adds the parent dirs for the entries with the correct uid/gid.
              // (otherwise various buildpack tasks won't be able to write to them!)
              addParents(tout, seenDirs, uid, gid, pathWithEntry);
              
              log.debug("adding "+pathWithEntry);
              // add this file entry.
              TarArchiveEntry tae = new TarArchiveEntry(pathWithEntry);
              tae.setSize(ve.getSize());
              tae.setUserId(uid);
              tae.setGroupId(gid);
              tout.putArchiveEntry(tae);
              ContentSupplier cs = ve.getContentSupplier();
              if(cs==null) {
                throw new IOException("Error ContentSupplier was not provided");
              }
              try (InputStream is = ve.getContentSupplier().getData();) {
                if(is==null) {
                  throw new IOException("Error ContentSupplier gave null for getData");
                }
                
                IOUtils.copy(is, tout);
                
              }
              tout.closeArchiveEntry();
            }
          } catch (IOException e) {
            writerException.set(e);
          }
        }
      };

      Runnable reader = new Runnable() {
        @Override
        public void run() {
          dc.copyArchiveToContainerCmd(containerId).withRemotePath("/").withTarInputStream(in).exec();
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
        throw new IOException(ie);
      }

      // did the write thread complete without issues? if not, bubble the cause.
      IOException wio = writerException.get();
      if (wio != null) {
        throw wio;
      }
    }
  }
}
