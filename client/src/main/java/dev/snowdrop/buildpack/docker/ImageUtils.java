package dev.snowdrop.buildpack.docker;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.NotFoundException;

import dev.snowdrop.buildpack.config.DockerConfig;
import dev.snowdrop.buildpack.config.ImageReference;
import dev.snowdrop.buildpack.config.DockerConfig.PullPolicy;
import dev.snowdrop.buildpack.BuildpackException;
/**
 * Higher level docker image api
 */
public class ImageUtils {
  private static final Logger log = LoggerFactory.getLogger(ImageUtils.class);

  public static class ImageInfo {
    public String id;
    public String digest;
    public String tags;
    public Map<String, String> labels;
    public String[] env;
    public String platform;
  }

  /**
   * Util method to pull images, configure behavior via dockerconfig.
   */
  @SuppressWarnings("resource")
  public static void pullImages(DockerConfig config, ImageReference... images) {
    ImageUtils.pullImages(config,null,images);
  }

  public static void pullImages(DockerConfig config, String platform, ImageReference... images) {

    if(config.getPullPolicy().equals(PullPolicy.NEVER)){
      log.debug("Image pull skipped due to policy NEVER");
      return;
    }

    //use toCollection HashSet new, to ensure mutability guarantee (toSet works today, but offers no mutability guarantees)
    Set<ImageReference> imageNameSet = Stream.of(images).collect(Collectors.toCollection(HashSet::new));

    DockerClient dc = config.getDockerClient();

    //if using ifnotpresent, filter set to unknown images.
    if(config.getPullPolicy().equals(DockerConfig.PullPolicy.IF_NOT_PRESENT)) {
      // list the current known images
      List<Image> li = dc.listImagesCmd().exec();

      log.debug("Requested Images "+imageNameSet);
      for (Image i : li) {
        if (i.getRepoTags() == null) {
          continue;
        }
        for (String it : i.getRepoTags()) {
          ImageReference test = new ImageReference(it);
          log.debug("IF_NOT_PRESENT evaluating known image : "+test.getCanonicalReference()+" ("+test.getReference()+")");
          if(!test.digestPresent() && "latest".equals(test.getTag())){
            //no tag, or tag is latest, and no digest, means we MUST pull the image (k8s pull policy overrides IF_NOT_PRESENT for :latest tags)
            if (imageNameSet.contains(test)) {
              log.debug("Image "+test+" Already Known, will be repulled as image using :latest tag");
            }
          }else{
            if (imageNameSet.contains(test)) {
              log.debug("Image "+test+" Already Known, will not repull image");
              imageNameSet.remove(test);
            }
          }
        }
      }

      if (imageNameSet.isEmpty()) {
        // fast exit if all images are already known to the local docker.
        log.debug("Nothing to pull, all of " + Arrays.asList(images) + " are known");
        return;
      }
    }

    int retryCount = 0;
    Map<String,PullImageResultCallback> pircMap = new HashMap<>();

    // pull the images still in set.
    for (ImageReference stillNeeded : imageNameSet) {
      log.debug("pulling '" + stillNeeded.getReferenceWithLatest() + "' "+(platform==null?"":" for platform "+platform));
      PullImageResultCallback pirc = new PullImageResultCallback();
      if(platform!=null){
        dc.pullImageCmd(stillNeeded.getReferenceWithLatest()).withPlatform(platform).exec(pirc);
      } else {
        dc.pullImageCmd(stillNeeded.getReferenceWithLatest()).exec(pirc);
      }
      pircMap.put(stillNeeded.getReferenceWithLatest(),pirc);
    }

    // wait for pulls to complete.
    RuntimeException lastSeen = null;
    boolean allDone = false;
    while(!allDone && retryCount<=config.getPullRetryCount()){
      allDone = true;
      long thisWait = config.getPullTimeoutSeconds()+(retryCount*config.getPullRetryIncreaseSeconds());
      for (Entry<String, PullImageResultCallback> e : pircMap.entrySet()) {
        boolean done = false;
        try {
          if(e.getValue()==null) continue;
          log.debug("waiting on image "+e.getKey()+" for "+thisWait+" seconds "+(platform==null?"":" for platform "+platform));
          done = e.getValue().awaitCompletion( thisWait, TimeUnit.SECONDS);
          if(done){ log.debug("success for image "+e.getKey()); }
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
          if(platform!=null){
            dc.pullImageCmd(imageName).withPlatform(platform).exec(newPirc);
          } else {
            dc.pullImageCmd(imageName).exec(newPirc);
          }
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
        List<String> remaining = pircMap.entrySet().stream().filter(e -> e.getValue()!=null).map(i -> i.getKey()).collect(Collectors.toList());
        if(!remaining.isEmpty()){
          log.debug("Retrying ("+retryCount+") for "+remaining);
        }
      }
    }

    if(lastSeen!=null && !allDone){
      throw lastSeen;
    }
  }


  /**
   * Util method to retrieve info for a given docker image.
   */
  public static ImageInfo inspectImage(DockerClient dc, ImageReference image) {
    String imageName = image.getReferenceWithLatest();
    InspectImageResponse iir = dc.inspectImageCmd(imageName).exec();
    // keep only some of the info.. 
    ImageInfo ii = new ImageInfo();
    ii.id = iir.getId();
    if( iir.getRepoDigests() != null ){
      ii.digest = iir.getRepoDigests().toString();
    }
    if ( iir.getRepoTags() != null){
      ii.tags = iir.getRepoTags().toString();
    }
    ii.labels = iir.getConfig().getLabels();
    ii.env = iir.getConfig().getEnv();
    if(iir.getArch()!=null && !iir.getArch().isEmpty() && iir.getOs()!=null && !iir.getOs().isEmpty()){
      ii.platform = iir.getOs()+"/"+iir.getArch();
    }else{
      ii.platform = null;
    }
    return ii;
  }
}
