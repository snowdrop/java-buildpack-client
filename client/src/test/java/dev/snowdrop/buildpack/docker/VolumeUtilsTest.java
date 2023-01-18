package dev.snowdrop.buildpack.docker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CopyArchiveToContainerCmd;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.CreateVolumeCmd;
import com.github.dockerjava.api.command.InspectVolumeCmd;
import com.github.dockerjava.api.command.InspectVolumeResponse;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.command.RemoveVolumeCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;

import dev.snowdrop.buildpack.BuildpackException;

@ExtendWith(MockitoExtension.class)
class VolumeUtilsTest {

  @Test
  void createVolumeIfRequired(@Mock DockerClient dc, @Mock CreateVolumeCmd cvc, @Mock InspectVolumeCmd ivc, @Mock InspectVolumeResponse ivr) {
    String volumeId = "fish";

    when(dc.createVolumeCmd()).thenReturn(cvc);
    when(cvc.withName(volumeId)).thenReturn(cvc);
    when(dc.inspectVolumeCmd(volumeId)).thenReturn(ivc);
    when(ivc.exec()).thenThrow(new NotFoundException("test")).thenReturn(ivr);

    boolean result = VolumeUtils.createVolumeIfRequired(dc, volumeId);

    assertEquals(result, true);
    verify(cvc, atLeastOnce()).withName(volumeId);
    verify(cvc).exec();
    verify(ivc, atLeastOnce()).exec();
  }

  @Test
  void createVolumeIfRequiredWhenNotRequired(@Mock DockerClient dc, @Mock InspectVolumeCmd ivc, @Mock InspectVolumeResponse ivr) {
    String volumeId = "fish";

    when(dc.inspectVolumeCmd(volumeId)).thenReturn(ivc);
    when(ivc.exec()).thenReturn(ivr);

    boolean result = VolumeUtils.createVolumeIfRequired(dc, volumeId);

    assertEquals(result, true);
    verify(ivc, atLeastOnce()).exec();
  }

  @Test
  void testRemoveVolume(@Mock DockerClient dc, @Mock RemoveVolumeCmd rvc)
  {
    String containerId = "id";
    when(dc.removeVolumeCmd(containerId)).thenReturn(rvc);
    VolumeUtils.removeVolume(dc, containerId);
    verify(rvc).exec();
  }
  
  

  @Test
  void addContentToVolumeViaString(@Mock DockerClient dc ) {

    String volumeName = "fish";
    String entryName = "patent";
    String entryContent = "stilettos";
    String containerId = "wibble";

    try (MockedStatic<ContainerUtils> scu = Mockito.mockStatic(ContainerUtils.class)){

        scu.when(() -> ContainerUtils.createContainer(eq(dc), anyString(), ArgumentMatchers.<VolumeBind>any())).thenReturn(containerId);

        boolean result = VolumeUtils.addContentToVolume(dc, volumeName, entryName, entryContent);
        assertTrue(result);

        scu.verify(() -> ContainerUtils.addContentToContainer(eq(dc), eq(containerId), anyString(), anyInt(), anyInt(), ArgumentMatchers.<ContainerEntry>argThat(ce -> {
            assertEquals(entryName, ce.getPath());
            assertEquals(entryContent.length(), ce.getSize());
            
            InputStream is = ce.getDataSupplier().getData();
            assertNotNull(is);
            try{
                BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                assertEquals(entryContent,br.readLine());
            }catch(Exception e){
                return false;
            }
            return true;
        })));
 
    }

  }

  @Test
  void addContentToContainerViaFile(@Mock DockerClient dc,
                                    @Mock File f,
                                    @Mock Path p,
                                    @Mock FileSystem fs,
                                    @Mock FileSystemProvider fsp,
                                    @Mock BasicFileAttributes bfa) {

    String volumeName = "fish";
    String containerId = "wibble";
    String pathInVolume = "path";
    String fileContent = "kitten";
    String fileName = "wibble";
    Long fileSize = Long.valueOf(fileContent.length());

    when(f.exists()).thenReturn(true);
    when(f.isFile()).thenReturn(true);
    when(f.isDirectory()).thenReturn(false);
    when(f.getName()).thenReturn(fileName);
    when(f.toPath()).thenReturn(p);

    when(p.getFileSystem()).thenReturn(fs);
    when(fs.provider()).thenReturn(fsp);

    try {
        when(fsp.newInputStream(eq(p), ArgumentMatchers.any())).thenReturn(new ByteArrayInputStream(fileContent.getBytes()));
        when(fsp.readAttributes(eq(p),eq(BasicFileAttributes.class))).thenReturn(bfa);
      } catch (IOException e)  {
        throw BuildpackException.launderThrowable(e);
      }
    when(bfa.size()).thenReturn(fileSize);

    try (MockedStatic<ContainerUtils> scu = Mockito.mockStatic(ContainerUtils.class)){

        scu.when(() -> ContainerUtils.createContainer(eq(dc), anyString(), ArgumentMatchers.<VolumeBind>any())).thenReturn(containerId);

        boolean result = VolumeUtils.addContentToVolume(dc, volumeName, pathInVolume, f);
        assertTrue(result);

        scu.verify(() -> ContainerUtils.addContentToContainer(eq(dc), eq(containerId), anyString(), anyInt(), anyInt(), ArgumentMatchers.<ContainerEntry>argThat(ce -> {
            assertEquals("/"+fileName, ce.getPath());
            assertEquals(fileSize, ce.getSize());
            
            try{
                InputStream is = ce.getDataSupplier().getData();

                //TODO: not sure why, but mockito invokes the verify twice, but the underlying stream is already depleted on the 2nd. 
                //      since it doesn't apply to the real code, just gate on when the stream has data to only check it the first time.
                if(is.available()>0){
                    assertNotNull(is);
                    String r = new BufferedReader(new InputStreamReader(is)).readLine();
                    assertEquals(fileContent, r);
                }

            }catch(Exception e){
                e.printStackTrace();
                return false;
            }
            return true;
        })));
    }
  }

}
