package dev.snowdrop.buildpack.docker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;

@ExtendWith(MockitoExtension.class)
class ContainerUtilsTest {

  @Test
  void createContainerWithoutCommandWithoutBinds(@Mock DockerClient dc, 
                                                 @Mock CreateContainerCmd ccc,
                                                 @Mock HostConfig hc, 
                                                 @Mock CreateContainerResponse ccr) {
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
  void createContainerWithCommandWithoutBinds(@Mock DockerClient dc, 
                                              @Mock CreateContainerCmd ccc, 
                                              @Mock HostConfig hc,
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
  void createContainerWithCommandWithBinds(@Mock DockerClient dc, 
                                           @Mock CreateContainerCmd ccc,
                                           @Mock HostConfig hc,
                                           @Mock CreateContainerResponse ccr) {
    String imageRef = "testImageRef";
    String containerId = "fish";
    List<String> commands = Arrays.asList(new String[]{"wibble"});
    
    VolumeBind one = new VolumeBind("one", "/1");
    VolumeBind two = new VolumeBind("two", "/2");
    
    when(dc.createContainerCmd(imageRef)).thenReturn(ccc);
    when(ccc.getHostConfig()).thenReturn(hc);
    when(ccc.exec()).thenReturn(ccr);
    when(ccr.getId()).thenReturn(containerId);
    
    String result = ContainerUtils.createContainer(dc, "testImageRef", commands, one, two);
    assertEquals(result,containerId);
    
    verify(ccc, atLeastOnce()).withUser("root");
    verify(ccc, atLeastOnce()).getHostConfig();
    
    //allow binds in either other.
    verify(hc, atLeastOnce()).withBinds(argThat(new ArgumentMatcher<List<Bind>>() {
      @Override
      public boolean matches(List<Bind> argument) {
        if(argument.size()==2) {
          return ( 
                   (
                    one.volumeName.equals(argument.get(0).getPath()) && two.volumeName.equals(argument.get(1).getPath()) &&
                    one.mountPath.equals(argument.get(0).getVolume().getPath()) && two.mountPath.equals(argument.get(1).getVolume().getPath())
                   )||(
                    one.volumeName.equals(argument.get(1).getPath()) && two.volumeName.equals(argument.get(0).getPath()) &&
                    one.mountPath.equals(argument.get(1).getVolume().getPath()) && two.mountPath.equals(argument.get(0).getVolume().getPath())
                   )
                 );
        }
        return false;
      }
    }));
    
    verify(ccc).exec();
    verify(ccr).getId();
    
    verify(ccc).withCmd( eq(commands) );
  }
  
  @Test
  void removeContainer(@Mock DockerClient dc,
                       @Mock RemoveContainerCmd rcc) {
    String id = "fish";
    
    when(dc.removeContainerCmd(id)).thenReturn(rcc);
    
    ContainerUtils.removeContainer(dc, id);
    
    verify(rcc).exec();
  }
  
  
}
