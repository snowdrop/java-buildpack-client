package dev.snowdrop.buildpack.docker;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CopyArchiveFromContainerCmd;
import com.github.dockerjava.api.command.CopyArchiveToContainerCmd;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;

import dev.snowdrop.buildpack.BuildpackException;
import dev.snowdrop.buildpack.docker.ContainerEntry.DataSupplier;


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
        return createContainer(dc, imageReference, command, 0, null, null, null, volumes);
  }

  public static String createContainer(DockerClient dc, String imageReference, List<String> command,
        Integer runAsId, Map<String,String> env, String securityOpts, String network,
        List<VolumeBind> volumes) {

    CreateContainerCmd ccc = dc.createContainerCmd(imageReference);
    if (volumes != null) {
      List<Bind> binds = new ArrayList<>();
      for (VolumeBind vb : volumes) {
        Bind bind = createBind(vb);
        binds.add(bind);
      }

      ccc.getHostConfig().withBinds(binds);
    }  

    return createContainerInternal(dc,imageReference,command,runAsId,env,securityOpts,network,ccc);
  }

  public static String createContainer(DockerClient dc, String imageReference, List<String> command,
        Integer runAsId, Map<String,String> env, String securityOpts, String network,
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

    return createContainerInternal(dc,imageReference,command,runAsId,env,securityOpts,network,ccc);
  }

  private static String createContainerInternal(DockerClient dc, String imageReference, List<String> command,
        Integer runAsId, Map<String,String> env, String securityOpts, String network, CreateContainerCmd ccc) {

    if(runAsId!=null){
        ccc.withUser(""+runAsId);
    }
    
    if(env!=null) {
      ccc.withEnv( env.entrySet().stream().map(e -> e.getKey()+"="+e.getValue()).collect(Collectors.toList()));
    }

    if(securityOpts!=null){
      ccc.withHostConfig(ccc.getHostConfig().withSecurityOpts(Collections.singletonList(securityOpts)));  
    }

    if (command != null) {
      ccc.withCmd(command);
    }

    if (network!=null){
       ccc.withHostConfig(ccc.getHostConfig().withNetworkMode(network));
    }

    CreateContainerResponse ccr = ccc.exec();

    return ccr.getId();
  }

  public static String commitContainer(DockerClient dc, String containerId) {
    return dc.commitCmd(containerId).exec();
  }  

  public static void removeContainer(DockerClient dc, String containerId) {
    dc.removeContainerCmd(containerId).withForce(true).exec();
  }

  public static void addContentToContainer(DockerClient dc, String containerId, List<ContainerEntry> entries) {
    addContentToContainer(dc, containerId, entries != null ? entries.toArray(new ContainerEntry[entries.size()]) : new ContainerEntry[0]);
  }
  
  public static void addContentToContainer(DockerClient dc, String containerId, ContainerEntry... entries) {
    addContentToContainer(dc, containerId, "", null, null, entries);
  }

  public static void addContentToContainer(DockerClient dc, String containerId, String pathInContainer, Integer userId,
      Integer groupId, File content) {
    addContentToContainer(dc, containerId, pathInContainer, userId, groupId, new FileContent("", content).getContainerEntries());
  }

  public static void addContentToContainer(DockerClient dc, String containerId, String pathInContainer, Integer userId,
      Integer groupId, String name, Integer mode, String content) {
    addContentToContainer(dc, containerId, pathInContainer, userId, groupId, new StringContent(name, mode, content).getContainerEntries());
  }

  /**
   * util method to add the parent dirs of a file/dir to an archive, to force them
   * to exist with the correct permissions, otherwise implicitly defined
   * directories were being created with perms preventing the buildpack from
   * executing as expected.
   */
  private static void addParents(TarArchiveOutputStream tout, Set<String> seenDirs, int uid, int gid, String path) {
    try {
      if (path.contains("/")) {
        String parent = path.substring(0, path.lastIndexOf("/"));
        boolean unknown = seenDirs.add(parent);
        // only need to follow this chain if we haven't done it already =)
        if (unknown) {
          // add parents of this FIRST
          addParents(tout, seenDirs, uid, gid, parent);
          
          log.debug("adding "+parent+"/ to tar");
          // and then add this =)
          TarArchiveEntry tae = new TarArchiveEntry(parent + "/");
          tae.setSize(0);
          tae.setUserId(uid);
          tae.setGroupId(gid);     
          tae.setMode(TarArchiveEntry.DEFAULT_DIR_MODE);
          tout.putArchiveEntry(tae);
          tout.closeArchiveEntry();
        }
      }
    } catch (IOException e) {
      throw BuildpackException.launderThrowable(e);
    }
  }

  /**
   * Adds content to the container, with specified uid/gid
   */
  public static void addContentToContainer(DockerClient dc, String containerId, String pathInContainer, Integer userId, Integer groupId, List<ContainerEntry> entries) {
    addContentToContainer(dc, containerId, pathInContainer, userId, groupId, entries != null ? entries.toArray(new ContainerEntry[entries.size()]) : new ContainerEntry[0]);
  }

  public static void addContentToContainer(DockerClient dc, String containerId, String pathInContainer, Integer userId, Integer groupId, ContainerEntry... entries) {

    log.info("Adding to container "+containerId+" pathInContainer "+pathInContainer);

    Set<String> seenDirs = new HashSet<>();
    // Don't add entry for "/", causes issues with tar format.
    seenDirs.add("");

    // use supplied pathInContainer, trim off trailing "/" where required.
    final String containerPath = (!pathInContainer.isEmpty() && pathInContainer.endsWith("/"))
        ? pathInContainer.substring(0, pathInContainer.length() - 1)
        : pathInContainer;

    // set uid/gid to the supplied values, or 0 if not supplied.
    final int uid = (userId != null) ? userId : 0;
    final int gid = (groupId != null) ? groupId : 0;

    try (PipedInputStream in = new PipedInputStream(4096); PipedOutputStream out = new PipedOutputStream(in)) {
      AtomicReference<Exception> writerException = new AtomicReference<>();

      Runnable writer = new Runnable() {
        @Override
        public void run() {
          try (TarArchiveOutputStream tout = new TarArchiveOutputStream(new GZIPOutputStream(new BufferedOutputStream(out)));) {
            tout.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            for (ContainerEntry ve : entries) {
              // prefix the entry path with the pathInContainer value.
              String entryPath = ve.getPath();
              
              if(entryPath==null || entryPath.isEmpty()) {
                throw new IOException("Error path was empty");
              }
              
              if (entryPath.startsWith("/"))
                entryPath = entryPath.substring(1);

              // important! adds the parent dirs for the entries with the correct uid/gid.
              // (otherwise various buildpack tasks won't be able to write to them!)
              addParents(tout, seenDirs, uid, gid, entryPath);
              
              log.debug("adding "+entryPath+" to tar");
              // add this file entry.
              TarArchiveEntry tae = new TarArchiveEntry(entryPath);
              tae.setSize(ve.getSize());
              tae.setUserId(uid);
              tae.setGroupId(gid);                            
              tae.setMode(0100000 + ve.getMode()); //0100000 means 'regular file'
              tout.putArchiveEntry(tae);
              DataSupplier cs = ve.getDataSupplier();
              if(cs==null) {
                throw new IOException("Error DataSupplier was not provided");
              }
              try (InputStream is = ve.getDataSupplier().getData();) {
                if(is==null) {
                  throw new IOException("Error DataSupplier gave null for getData");
                }
                
                copy(is, tout);
                
              }
              tout.closeArchiveEntry();
            } 
          } catch (Exception e) {
            writerException.set(e);
          }
        } 
        };

      log.info("Copying archive to container at "+containerPath);

      Runnable reader = new Runnable() {
        @Override
        public void run() {
          CopyArchiveToContainerCmd c = dc.copyArchiveToContainerCmd(containerId)
                                          .withRemotePath(containerPath)
                                          .withTarInputStream(in);
          c.exec();
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

  public static byte[] getFileFromContainer(DockerClient dc, String id, String path) {
    CopyArchiveFromContainerCmd copycmd = dc.copyArchiveFromContainerCmd(id, path);    
    ByteArrayOutputStream file = new ByteArrayOutputStream();
    try{
      InputStream tarStream = copycmd.exec();
      TarArchiveInputStream tarInput = new TarArchiveInputStream(tarStream);
      try{
        TarArchiveEntry tarEntry = tarInput.getNextTarEntry();
        while(tarEntry!=null){
            copy(tarInput, file);
            file.close();           
            tarEntry = tarInput.getNextTarEntry();
        }
        return file.toByteArray();
      }finally{
        if(tarInput!=null)tarInput.close();
      }
    }catch(NotFoundException nfe){
        throw BuildpackException.launderThrowable("Unable to locate container '"+id+"'", nfe);
    } catch (IOException e) {
        throw BuildpackException.launderThrowable("Unable to retrieve '"+path+"' from container", e);
    }    
  }

  private static final void copy(InputStream in, OutputStream out) {
    byte[] buf = new byte[8192];
    int length;
    try {
      while ((length = in.read(buf)) > 0) {
        out.write(buf, 0, length);
      }
    } catch (IOException e) {
      throw BuildpackException.launderThrowable(e);
    }
  }
}
