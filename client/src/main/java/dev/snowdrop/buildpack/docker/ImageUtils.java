package dev.snowdrop.buildpack.docker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.NotFoundException;

import dev.snowdrop.buildpack.config.DockerConfig;
import dev.snowdrop.buildpack.BuildpackException;
/**
 * Higher level docker image api
 */
public class ImageUtils {
  private static final Logger log = LoggerFactory.getLogger(ImageUtils.class);

  public static class ImageInfo {
    public String id;
    public Map<String, String> labels;
    public String[] env;
  }

  /**
   * Util method to pull images, configure behavior via dockerconfig.
   */
  @SuppressWarnings("resource")
  public static void pullImages(DockerConfig config, String... imageNames) {
    Set<String> imageNameSet = new HashSet<>(Arrays.asList(imageNames));

    DockerClient dc = config.getDockerClient();

    //if using ifnotpresent, filter set to unknown images.
    if(config.getPullPolicy() == DockerConfig.PullPolicy.IF_NOT_PRESENT) {
      // list the current known images
      List<Image> li = dc.listImagesCmd().exec();
      for (Image i : li) {
        if (i.getRepoTags() == null) {
          continue;
        }
        for (String it : i.getRepoTags()) {
          if (imageNameSet.contains(it)) {
            imageNameSet.remove(it);
          }
        }
      }

      if (imageNameSet.isEmpty()) {
        // fast exit if all images are already known to the local docker.
        log.debug("Nothing to pull, all of " + Arrays.asList(imageNames) + " are known");
        return;
      }
    }

    int retryCount = 0;
    Map<String,PullImageResultCallback> pircMap = new HashMap<>();

    // pull the images still in set.
    for (String stillNeeded : imageNameSet) {
      log.debug("pulling '" + stillNeeded + "'");
      PullImageResultCallback pirc = new PullImageResultCallback();
      dc.pullImageCmd(stillNeeded).exec(pirc);
      pircMap.put(stillNeeded,pirc);
    }

    // wait for pulls to complete.
    RuntimeException lastSeen = null;
    boolean allDone = false;
    while(!allDone && retryCount<=config.getPullRetryCount()){
      allDone = true;
      long thisWait = config.getPullTimeout()+(retryCount*config.getPullRetryIncrease());
      for (Entry<String, PullImageResultCallback> e : pircMap.entrySet()) {
        boolean done = false;
        try {
          if(e.getValue()==null) continue;
          log.debug("waiting on image "+e.getKey()+" for "+thisWait+" seconds");
          done = e.getValue().awaitCompletion( thisWait, TimeUnit.SECONDS);
          log.debug("success for image "+e.getKey());
        } catch (InterruptedException ie) {
          throw BuildpackException.launderThrowable(ie);
        } catch (DockerClientException dce) {
          //error occurred during pull for this pirc, need to pause & retry the pull op
          lastSeen = dce;
        } catch (NotFoundException nfe) {
          lastSeen = nfe;
        }
        if(!done){
          String imageName = e.getKey();
          PullImageResultCallback newPirc = new PullImageResultCallback();
          dc.pullImageCmd(imageName).exec(newPirc);
          e.setValue(newPirc);
          allDone=false;      
        }else{
          e.setValue(null);
        }
      }
      retryCount++;
      if(retryCount<=config.getPullRetryCount()){
        if(lastSeen!=null){
          log.debug("Error during pull "+lastSeen.getMessage());
        }
        log.debug("Retrying ("+retryCount+") for "+pircMap.entrySet().stream().filter(e -> e.getValue()!=null).collect(Collectors.toList()));
      }
    }

    if(lastSeen!=null && !allDone){
      throw lastSeen;
    }
  }


  /**
   * Util method to retrieve info for a given docker image.
   */
  public static ImageInfo inspectImage(DockerClient dc, String imageName) {
    InspectImageResponse iir = dc.inspectImageCmd(imageName).exec();
    // today we just keep the id/labels/env, can expand if needed.
    ImageInfo ii = new ImageInfo();
    ii.id = iir.getId();
    ii.labels = iir.getConfig().getLabels();
    ii.env = iir.getConfig().getEnv();
    return ii;
  }
}
