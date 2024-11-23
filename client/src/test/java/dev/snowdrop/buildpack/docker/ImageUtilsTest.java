package dev.snowdrop.buildpack.docker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.atLeast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.ListImagesCmd;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.model.ContainerConfig;
import com.github.dockerjava.api.model.Image;

import dev.snowdrop.buildpack.config.DockerConfig;
import dev.snowdrop.buildpack.config.ImageReference;
import dev.snowdrop.buildpack.docker.ImageUtils.ImageInfo;

@ExtendWith(MockitoExtension.class)
public class ImageUtilsTest {

  
  @Test
  void testInspectImage(@Mock DockerClient dc,
      @Mock InspectImageCmd iic,
      @Mock InspectImageResponse iir,
      @Mock ContainerConfig cc
      ) {
    
    ImageReference test = new ImageReference("test");
    String imageName = "test:latest";
    
    when(dc.inspectImageCmd(eq(imageName))).thenReturn(iic);
    when(iic.exec()).thenReturn(iir);
    
    when(iir.getId()).thenReturn("id");
    when(iir.getConfig()).thenReturn(cc);
    when(cc.getEnv()).thenReturn(new String[] {"one","two"});
    Map<String,String> labels = new HashMap<String,String>();
    labels.put("l1", "v1");
    when(cc.getLabels()).thenReturn(labels);
    
    ImageInfo ii = ImageUtils.inspectImage(dc, test);
    
    assertEquals(ii.id,"id");
    assertArrayEquals(ii.env, new String[] {"one","two"} );
    assertEquals(ii.labels, labels);
    
    verify(iic).exec();
  }
  
  @Test
  void testPullImageSingleUnknown(@Mock DockerConfig config, 
      @Mock DockerClient dc,
      @Mock ListImagesCmd lic,
      @Mock PullImageCmd pic) throws InterruptedException {
    
    ImageReference test = new ImageReference("test");
    String imageName = "test:latest";
    
    lenient().when(config.getDockerClient()).thenReturn(dc);
    lenient().when(config.getPullPolicy()).thenReturn(DockerConfig.PullPolicy.IF_NOT_PRESENT);
    lenient().when(dc.listImagesCmd()).thenReturn(lic);
    lenient().when(lic.exec()).thenReturn(new ArrayList<Image>());
    
    when(dc.pullImageCmd(eq(imageName))).thenReturn(pic);
    
    ImageUtils.pullImages(config, test);
    
    verify(pic, atLeast(1)).exec(ArgumentMatchers.any());
  }
  
  @Test
  void testPullImageSingleKnown(@Mock DockerConfig config, 
      @Mock DockerClient dc,
      @Mock ListImagesCmd lic,
      @Mock Image i,
      @Mock PullImageCmd pic) throws InterruptedException {
    
    ImageReference test = new ImageReference("test:v1");
    String imageName = "test:v1";

    lenient().when(config.getDockerClient()).thenReturn(dc);
    lenient().when(config.getPullPolicy()).thenReturn(DockerConfig.PullPolicy.IF_NOT_PRESENT);
    lenient().when(dc.listImagesCmd()).thenReturn(lic);

    List<Image> li = new ArrayList<Image>();
    li.add(i);
    when(lic.exec()).thenReturn(li);
    when(i.getRepoTags()).thenReturn(new String[] {imageName});

    //(dc.pullImageCmd(eq(imageName))).thenReturn(pic);
    
    ImageUtils.pullImages(config, test);
    
    verify(dc, never()).pullImageCmd(ArgumentMatchers.any());
  }

  @Test
  void testPullImageSingleKnownNoTag(@Mock DockerConfig config, 
      @Mock DockerClient dc,
      @Mock ListImagesCmd lic,
      @Mock Image i,
      @Mock PullImageCmd pic) throws InterruptedException {
    
    ImageReference test = new ImageReference("test");
    String imageName = "test:latest";

    lenient().when(config.getDockerClient()).thenReturn(dc);
    lenient().when(config.getPullPolicy()).thenReturn(DockerConfig.PullPolicy.IF_NOT_PRESENT);
    lenient().when(dc.listImagesCmd()).thenReturn(lic);

    List<Image> li = new ArrayList<Image>();
    li.add(i);
    when(lic.exec()).thenReturn(li);
    when(i.getRepoTags()).thenReturn(new String[] {imageName});

    when(dc.pullImageCmd(eq(imageName))).thenReturn(pic);
    
    ImageUtils.pullImages(config, test);
    
    verify(pic, atLeast(1)).exec(ArgumentMatchers.any());
  }

  @Test
  void testPullImageSingleKnownLatest(@Mock DockerConfig config, 
      @Mock DockerClient dc,
      @Mock ListImagesCmd lic,
      @Mock Image i,
      @Mock PullImageCmd pic) throws InterruptedException {
    
    ImageReference test = new ImageReference("test:latest");
    String imageName = "test:latest";

    lenient().when(config.getDockerClient()).thenReturn(dc);
    lenient().when(config.getPullPolicy()).thenReturn(DockerConfig.PullPolicy.IF_NOT_PRESENT);
    lenient().when(dc.listImagesCmd()).thenReturn(lic);

    List<Image> li = new ArrayList<Image>();
    li.add(i);
    when(lic.exec()).thenReturn(li);
    when(i.getRepoTags()).thenReturn(new String[] {imageName});

    when(dc.pullImageCmd(eq(imageName))).thenReturn(pic);
    
    ImageUtils.pullImages(config, test);
    
    verify(pic, atLeast(1)).exec(ArgumentMatchers.any());
  }
  
}
