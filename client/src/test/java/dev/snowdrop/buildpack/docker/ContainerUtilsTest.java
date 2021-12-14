package dev.snowdrop.buildpack.docker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.spi.FileSystemProvider;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CopyArchiveToContainerCmd;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;

@ExtendWith(MockitoExtension.class)
class ContainerUtilsTest {

  @Test
  void createContainerWithoutCommandWithoutBinds(@Mock DockerClient dc, @Mock CreateContainerCmd ccc,
      @Mock HostConfig hc, @Mock CreateContainerResponse ccr) {
    String imageRef = "testImageRef";
    String containerId = "fish";

    when(dc.createContainerCmd(imageRef)).thenReturn(ccc);
    when(ccc.getHostConfig()).thenReturn(hc);
    when(ccc.exec()).thenReturn(ccr);
    when(ccr.getId()).thenReturn(containerId);

    String result = ContainerUtils.createContainer(dc, "testImageRef");
    assertEquals(result, containerId);

    verify(ccc, atLeastOnce()).withUser("root");
    verify(ccc, atLeastOnce()).getHostConfig();
    verify(hc, atLeastOnce()).withBinds(eq(Collections.<Bind>emptyList()));
    verify(ccc).exec();
    verify(ccr).getId();

    verify(ccc, never()).withCmd();
  }

  @Test
  void createContainerWithCommandWithoutBinds(@Mock DockerClient dc, @Mock CreateContainerCmd ccc, @Mock HostConfig hc,
      @Mock CreateContainerResponse ccr) {
    String imageRef = "testImageRef";
    String containerId = "fish";
    List<String> commands = Arrays.asList(new String[] { "wibble" });

    when(dc.createContainerCmd(imageRef)).thenReturn(ccc);
    when(ccc.getHostConfig()).thenReturn(hc);
    when(ccc.exec()).thenReturn(ccr);
    when(ccr.getId()).thenReturn(containerId);

    String result = ContainerUtils.createContainer(dc, "testImageRef", commands);
    assertEquals(result, containerId);

    verify(ccc, atLeastOnce()).withUser("root");
    verify(ccc, atLeastOnce()).getHostConfig();
    verify(hc, atLeastOnce()).withBinds(eq(Collections.<Bind>emptyList()));
    verify(ccc).exec();
    verify(ccr).getId();

    verify(ccc).withCmd(eq(commands));
  }

  @Test
  void createContainerWithCommandWithBinds(@Mock DockerClient dc, @Mock CreateContainerCmd ccc, @Mock HostConfig hc,
      @Mock CreateContainerResponse ccr) {
    String imageRef = "testImageRef";
    String containerId = "fish";
    List<String> commands = Arrays.asList(new String[] { "wibble" });

    VolumeBind one = new VolumeBind("one", "/1");
    VolumeBind two = new VolumeBind("two", "/2");

    when(dc.createContainerCmd(imageRef)).thenReturn(ccc);
    when(ccc.getHostConfig()).thenReturn(hc);
    when(ccc.exec()).thenReturn(ccr);
    when(ccr.getId()).thenReturn(containerId);

    String result = ContainerUtils.createContainer(dc, "testImageRef", commands, one, two);
    assertEquals(result, containerId);

    verify(ccc, atLeastOnce()).withUser("root");
    verify(ccc, atLeastOnce()).getHostConfig();

    // allow binds in either other.
    verify(hc, atLeastOnce()).withBinds(argThat(new ArgumentMatcher<List<Bind>>() {
      @Override
      public boolean matches(List<Bind> argument) {
        if (argument.size() == 2) {
          return ((one.volumeName.equals(argument.get(0).getPath()) && two.volumeName.equals(argument.get(1).getPath())
              && one.mountPath.equals(argument.get(0).getVolume().getPath())
              && two.mountPath.equals(argument.get(1).getVolume().getPath()))
              || (one.volumeName.equals(argument.get(1).getPath()) && two.volumeName.equals(argument.get(0).getPath())
                  && one.mountPath.equals(argument.get(1).getVolume().getPath())
                  && two.mountPath.equals(argument.get(0).getVolume().getPath())));
        }
        return false;
      }
    }));

    verify(ccc).exec();
    verify(ccr).getId();

    verify(ccc).withCmd(eq(commands));
  }

  @Test
  void removeContainer(@Mock DockerClient dc, @Mock RemoveContainerCmd rcc) {
    String id = "fish";

    when(dc.removeContainerCmd(id)).thenReturn(rcc);

    ContainerUtils.removeContainer(dc, id);

    verify(rcc).exec();
  }

  @Test
  void addContentToContainerViaString(@Mock DockerClient dc, @Mock CopyArchiveToContainerCmd catcc) throws IOException {

    String content = "Wibble";
    String contentPath = "/one";
    String contentName = "testfile";

    int userid = 123;
    int group = 456;

    String containerId = "id";

    when(dc.copyArchiveToContainerCmd(containerId)).thenReturn(catcc);
    when(catcc.withRemotePath(anyString())).thenReturn(catcc);
    when(catcc.withTarInputStream(argThat(x -> {
      if (x != null) {
        try (BufferedInputStream bis = new BufferedInputStream(x);
            GzipCompressorInputStream gzip = new GzipCompressorInputStream(bis);
            TarArchiveInputStream tais = new TarArchiveInputStream(gzip)) {
          TarArchiveEntry entry;
          while ((entry = (TarArchiveEntry) tais.getNextEntry()) != null) {
            if (!entry.isDirectory()) {
              if (entry.getName().equals(contentPath.substring(1) + "/" + contentName) && entry.getLongUserId() == userid
                  && entry.getLongGroupId() == group) {
                return true;
              }
            }
          }
          return false;
        } catch (IOException e) {
          System.err.println("Error during streamclose");
          e.printStackTrace();
          return false;
        }
      } else {
        return false;
      }

    }))).thenReturn(catcc);

    ContainerUtils.addContentToContainer(dc, containerId, contentPath, userid, group, contentName, content);

    verify(catcc).exec();
  }

  @Test
  void addContentToContainerViaStringWithoutUserIdAndGroup(@Mock DockerClient dc, @Mock CopyArchiveToContainerCmd catcc)
      throws IOException {

    String content = "Wibble";
    String contentPath = "/one";
    String contentName = "testfile";

    String containerId = "id";

    when(dc.copyArchiveToContainerCmd(containerId)).thenReturn(catcc);
    when(catcc.withRemotePath(anyString())).thenReturn(catcc);
    when(catcc.withTarInputStream(argThat(x -> {
      if (x != null) {
        try (BufferedInputStream bis = new BufferedInputStream(x);
            GzipCompressorInputStream gzip = new GzipCompressorInputStream(bis);
            TarArchiveInputStream tais = new TarArchiveInputStream(gzip)) {
          TarArchiveEntry entry;
          while ((entry = (TarArchiveEntry) tais.getNextEntry()) != null) {
            if (!entry.isDirectory()) {
              if (entry.getName().equals(contentPath.substring(1) + "/" + contentName) && entry.getLongUserId() == 0
                  && entry.getLongGroupId() == 0) {
                return true;
              }
            }
          }
          return false;
        } catch (IOException e) {
          System.err.println("Error during streamclose");
          e.printStackTrace();
          return false;
        }
      } else {
        return false;
      }

    }))).thenReturn(catcc);

    ContainerUtils.addContentToContainer(dc, containerId, contentPath, null, null, contentName, content);

    verify(catcc).exec();
  }

  @Test
  void addContentToContainerViaBrokenPathContentEntry(@Mock DockerClient dc, @Mock CopyArchiveToContainerCmd catcc)
      throws IOException {

    String containerId = "id";

    when(dc.copyArchiveToContainerCmd(containerId)).thenReturn(catcc);
    when(catcc.withRemotePath(anyString())).thenReturn(catcc);
    when(catcc.withTarInputStream(ArgumentMatchers.any())).thenReturn(catcc);

    ContainerEntry ce = new ContainerEntry() {
      public String getPath() {
        return null;
      }

      public long getSize() throws IOException {
        return 0;
      }

      public ContentSupplier getContentSupplier() throws IOException {
        return null;
      }
    };
    Exception e = assertThrows(IOException.class, () -> {
      ContainerUtils.addContentToContainer(dc, containerId, ce);
    });

    verify(catcc).exec();
  }

  @Test
  void addContentToContainerViaBrokenSizeContentEntry(@Mock DockerClient dc, @Mock CopyArchiveToContainerCmd catcc)
      throws IOException {

    String containerId = "id";

    when(dc.copyArchiveToContainerCmd(containerId)).thenReturn(catcc);
    when(catcc.withRemotePath(anyString())).thenReturn(catcc);
    when(catcc.withTarInputStream(ArgumentMatchers.any())).thenReturn(catcc);

    ContainerEntry ce = new ContainerEntry() {
      public String getPath() {
        return "fish";
      }

      public long getSize() throws IOException {
        throw new IOException("Test");
      }

      public ContentSupplier getContentSupplier() throws IOException {
        return null;
      }
    };
    Exception e = assertThrows(IOException.class, () -> {
      ContainerUtils.addContentToContainer(dc, containerId, ce);
    });

    verify(catcc).exec();
  }

  @Test
  void addContentToContainerViaMissingContentSupplier(@Mock DockerClient dc, @Mock CopyArchiveToContainerCmd catcc)
      throws IOException {

    String containerId = "id";

    when(dc.copyArchiveToContainerCmd(containerId)).thenReturn(catcc);
    when(catcc.withRemotePath(anyString())).thenReturn(catcc);
    when(catcc.withTarInputStream(ArgumentMatchers.any())).thenReturn(catcc);

    ContainerEntry ce = new ContainerEntry() {
      public String getPath() {
        return "fish";
      }

      public long getSize() throws IOException {
        return 4;
      }

      public ContentSupplier getContentSupplier() throws IOException {
        return null;
      }
    };
    Exception e = assertThrows(IOException.class, () -> {
      ContainerUtils.addContentToContainer(dc, containerId, ce);
    });
    verify(catcc).exec();
  }

  @Test
  void addContentToContainerViaBrokenContentSupplier(@Mock DockerClient dc, @Mock CopyArchiveToContainerCmd catcc)
      throws IOException {

    String containerId = "id";

    when(dc.copyArchiveToContainerCmd(containerId)).thenReturn(catcc);
    when(catcc.withRemotePath(anyString())).thenReturn(catcc);
    when(catcc.withTarInputStream(ArgumentMatchers.any())).thenReturn(catcc);

    ContainerEntry ce = new ContainerEntry() {
      public String getPath() {
        return "fish";
      }

      public long getSize() throws IOException {
        return 4;
      }

      public ContentSupplier getContentSupplier() throws IOException {
        return new ContentSupplier() {
          public InputStream getData() throws IOException {
            throw new IOException("Test");
          }
        };
      }
    };
    Exception e = assertThrows(IOException.class, () -> {
      ContainerUtils.addContentToContainer(dc, containerId, ce);
    });
    verify(catcc).exec();
  }

  @Test
  void addContentToContainerViaContentSupplierWithNullData(@Mock DockerClient dc, @Mock CopyArchiveToContainerCmd catcc)
      throws IOException {

    String containerId = "id";

    when(dc.copyArchiveToContainerCmd(containerId)).thenReturn(catcc);
    when(catcc.withRemotePath(anyString())).thenReturn(catcc);
    when(catcc.withTarInputStream(ArgumentMatchers.any())).thenReturn(catcc);

    ContainerEntry ce = new ContainerEntry() {
      public String getPath() {
        return "fish";
      }

      public long getSize() throws IOException {
        return 4;
      }

      public ContentSupplier getContentSupplier() throws IOException {
        return new ContentSupplier() {
          public InputStream getData() throws IOException {
            return null;
          }
        };
      }
    };
    Exception e = assertThrows(IOException.class, () -> {
      ContainerUtils.addContentToContainer(dc, containerId, ce);
    });
    verify(catcc).exec();
  }

  @Test
  void addContentToContainerViaFile(@Mock DockerClient dc,
      @Mock CopyArchiveToContainerCmd catcc, 
      @Mock File f,
      @Mock Path p,
      @Mock FileSystem fs,
      @Mock FileSystemProvider fsp,
      @Mock BasicFileAttributes bfa) throws IOException {

    String containerId = "id";

    when(dc.copyArchiveToContainerCmd(containerId)).thenReturn(catcc);
    when(catcc.withRemotePath(anyString())).thenReturn(catcc);
    when(catcc.withTarInputStream(argThat(x -> {
      if (x != null) {
        try (BufferedInputStream bis = new BufferedInputStream(x);
            GzipCompressorInputStream gzip = new GzipCompressorInputStream(bis);
            TarArchiveInputStream tais = new TarArchiveInputStream(gzip)) {
          TarArchiveEntry entry;
          while ((entry = (TarArchiveEntry) tais.getNextEntry()) != null) {
            if (!entry.isDirectory()) {
              if (entry.getName().equals("wibble") && entry.getLongUserId() == 0
                  && entry.getLongGroupId() == 0 && entry.getSize()==4L) {
                return true;
              }
            }
          }
          return false;
        } catch (IOException e) {
          System.err.println("Error during streamclose");
          e.printStackTrace();
          return false;
        }
      } else {
        return false;
      }
    }))).thenReturn(catcc);
    
    when(f.exists()).thenReturn(true);
    when(f.isFile()).thenReturn(true);
    when(f.isDirectory()).thenReturn(false);
    when(f.getName()).thenReturn("wibble");
    when(f.toPath()).thenReturn(p);
    
    when(p.getFileSystem()).thenReturn(fs);
    when(fs.provider()).thenReturn(fsp);
    
    when(fsp.newInputStream(eq(p), ArgumentMatchers.any())).thenReturn(new ByteArrayInputStream("fish".getBytes()));
    when(fsp.readAttributes(eq(p),eq(BasicFileAttributes.class))).thenReturn(bfa);
    when(bfa.size()).thenReturn(4L);
    
    ContainerUtils.addContentToContainer(dc, containerId, "/", 0,0, f);

    verify(catcc).exec();
  }

}
