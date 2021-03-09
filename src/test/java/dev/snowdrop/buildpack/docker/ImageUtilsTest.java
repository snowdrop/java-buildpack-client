package dev.snowdrop.buildpack.docker;


import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.ListImagesCmd;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.model.ContainerConfig;
import com.github.dockerjava.api.model.Image;

import dev.snowdrop.buildpack.docker.ImageUtils.ImageInfo;

@ExtendWith(MockitoExtension.class)
public class ImageUtilsTest {

  
  @Test
  void testInspectImage(@Mock DockerClient dc,
      @Mock InspectImageCmd iic,
      @Mock InspectImageResponse iir,
      @Mock ContainerConfig cc
      ) {
    
    String imageName="test";
    
    when(dc.inspectImageCmd(eq(imageName))).thenReturn(iic);
    when(iic.exec()).thenReturn(iir);
    
    when(iir.getId()).thenReturn("id");
    when(iir.getConfig()).thenReturn(cc);
    when(cc.getEnv()).thenReturn(new String[] {"one","two"});
    Map<String,String> labels = new HashMap<String,String>();
    labels.put("l1", "v1");
    when(cc.getLabels()).thenReturn(labels);
    
    ImageInfo ii = ImageUtils.inspectImage(dc, imageName);
    
    assertEquals(ii.id,"id");
    assertArrayEquals(ii.env, new String[] {"one","two"} );
    assertEquals(ii.labels, labels);
    
    verify(iic).exec();
  }
  
  @Test
  void testPullImageSingleUnknown(@Mock DockerClient dc,
      @Mock ListImagesCmd lic,
      @Mock PullImageCmd pic) throws InterruptedException {
    
    String imageName = "test";
    
    when(dc.listImagesCmd()).thenReturn(lic);
    when(lic.exec()).thenReturn(new ArrayList<Image>());
    
    when(dc.pullImageCmd(eq(imageName))).thenReturn(pic);
    
    ImageUtils.pullImages(dc, 0, imageName);
    
    verify(pic).exec(ArgumentMatchers.any());
  }
  
  @Test
  void testPullImageSingleKnown(@Mock DockerClient dc,
      @Mock ListImagesCmd lic,
      @Mock Image i,
      @Mock PullImageCmd pic) throws InterruptedException {
    
    String imageName = "test";
    
    when(dc.listImagesCmd()).thenReturn(lic);
    List<Image> li = new ArrayList<Image>();
    li.add(i);
    when(lic.exec()).thenReturn(li);
    when(i.getRepoTags()).thenReturn(new String[] {imageName});

    //when(dc.pullImageCmd(eq(imageName))).thenReturn(pic);
    
    ImageUtils.pullImages(dc, 0, imageName);
    
    verify(pic, never()).exec(ArgumentMatchers.any());
  }
  
}
