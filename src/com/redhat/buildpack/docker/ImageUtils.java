package com.redhat.buildpack.docker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.Image;

/**
 * Higher level docker image api
 */
public class ImageUtils {
	
	public static class ImageInfo {
		public String id;
		public Map<String,String> labels;
		public String[] env;
	}
	
	/**
	 * Util method to pull images if they don't exist to the local docker yet.
	 */
	public static void pullImages(DockerClient dc, String... imageNames) throws InterruptedException {
		Set<String> imageNameSet = new HashSet<>(Arrays.asList(imageNames));

		//list the current known images
		List<Image> li = dc.listImagesCmd().exec();
		for(Image i: li) {
			for(String it : i.getRepoTags()) {
				if(imageNameSet.contains(it)) {
					imageNameSet.remove(it);
				}
			}
		}

		//pull the images not known
		List<PullImageResultCallback> pircs = new ArrayList<>();
		for(String stillNeeded : imageNameSet) {
			PullImageResultCallback pirc = new PullImageResultCallback();
			dc.pullImageCmd(stillNeeded).exec(pirc);
			pircs.add(pirc);
		}

		//wait for pulls to complete.
		for(PullImageResultCallback pirc : pircs) {
			pirc.awaitCompletion(30, TimeUnit.SECONDS);
		}

		//TODO: progress tracking.. 
	}
	
	/**
	 * Util method to retrieve info for a given docker image. 
	 */
	public static ImageInfo inspectImage(DockerClient dc, String imageName) {
		InspectImageResponse iir = dc.inspectImageCmd(imageName).exec();
		iir.getConfig().getLabels();
		// today we just keep the id/labels/env, can expand if needed.
		ImageInfo ii = new ImageInfo();
		ii.id = iir.getId();
		ii.labels = iir.getConfig().getLabels();
		ii.env = iir.getConfig().getEnv();
		return ii;
	}
}
