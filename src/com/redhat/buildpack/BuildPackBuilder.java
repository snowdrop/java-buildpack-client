package com.redhat.buildpack;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.util.Arrays;
import java.util.Random;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.redhat.buildpack.docker.ContainerUtils;
import com.redhat.buildpack.docker.DockerClientUtils;
import com.redhat.buildpack.docker.ImageUtils;
import com.redhat.buildpack.docker.ImageUtils.ImageInfo;
import com.redhat.buildpack.docker.VolumeBind;
import com.redhat.buildpack.docker.VolumeUtils;

public class BuildPackBuilder {
	
	private final String PLATFORM_API_VERSION="0.5";
	
	private final String BUILD_VOL_PATH = "/bld";
	private final String LAUNCH_VOL_PATH = "/run";
	private final String APP_VOL_PATH = "/app";
	private final String OUTPUT_VOL_PATH = "/out";
	private final String ENV_VOL_PATH = "/env";
	
	private String buildImage;
	private String runImage;
	private String finalImage;
	private File appDir;
	private DockerClient dc;
	
	private int userId=0;
	private int groupId=0;

	public BuildPackBuilder(String buildImage, String runImage, String finalImage, File appDir) {
		this.buildImage = buildImage;
		this.runImage = runImage;
		this.finalImage = finalImage;
		this.appDir = appDir;
		this.dc = DockerClientUtils.getDockerClient();
	}
	
	private String getValue(JsonNode root, String path) {
		String[] parts = path.split("/");
		JsonNode next = root.get(parts[0]);
		if(next!=null && parts.length>1) {
			return getValue(next,path.substring(path.indexOf("/")+1));
		}
		if(next==null) {
			return null;
		}
		return next.asText();
	}
	
	public void prep() throws Exception {		

		ImageInfo ii = ImageUtils.inspectImage(dc,buildImage);
		
		//read the userid/groupid for the buildpack from it's env.
		for(String s : ii.env) {
			if(s.startsWith("CNB_USER_ID=")) {
				userId = Integer.valueOf(s.substring("CNB_USER_ID=".length()));
			}
			if(s.startsWith("CNB_GROUP_ID=")) {
				groupId = Integer.valueOf(s.substring("CNB_GROUP_ID=".length()));
			}
		}
		
		//pull the buildpack metadata json.
		String metadataJson = ii.labels.get("io.buildpacks.builder.metadata");			
		ObjectMapper om = new ObjectMapper();
		om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		JsonNode root = om.readTree(metadataJson);
		
		//read the buildpacks recommended runImage
		String ri = getValue(root,"stack/runImage/image");
		System.out.println("Got builder specified ri "+ri);

		//if caller didn't set runImage, use one from buildPack.
		if(runImage==null) {			
			if(ri==null) {
				throw new Exception("No runImage specified, and builderImage is missing metadata declaration");
			}else {
				if(ri.startsWith("index.docker.io/")) {
					ri = ri.substring("index.docker.io/".length());
				}
				runImage = ri;
			}	
		}

		//pull the buildImage and runImage.
		ImageUtils.pullImages(dc, buildImage, runImage);
	}
	
	//daft little adapter class to read log output from docker-java and spew it to stdout
	//we strip ansi color escape sequences for clarity.
	private static class StdOutContainerLog extends ResultCallback.Adapter<Frame>{
		@Override
		public void onNext(Frame object) {
			if(StreamType.STDOUT == object.getStreamType()) {
				System.out.print(" STDOUT> "+new String(object.getPayload(), UTF_8).replaceAll("[^m]+m", ""));
			}else if(StreamType.STDERR == object.getStreamType()) {
				System.err.print(" STDERR>"+new String(object.getPayload(), UTF_8).replaceAll("[^m]+m", ""));
		}
		}	
	}
	
	//run the build.. 
	public void build() throws Exception {
		//TODO: cache & launch volumes should be tied to the build being performed.. 
		//at the mo they are unique for every build, which is functional, but terrible for performance 
		//with repeated builds.
		String buildCacheVolume = "build-"+randomString(10);
		String launchCacheVolume = "launch-"+randomString(10);
		String applicationVolume = "app-"+randomString(10);
		String outputVolume = "output-"+randomString(10);
		String envVolume = "env-"+randomString(10);
		
		//create all the volumes we plan to use =)
		VolumeUtils.createVolumeIfRequired(dc, buildCacheVolume);
		VolumeUtils.createVolumeIfRequired(dc, launchCacheVolume);
		VolumeUtils.createVolumeIfRequired(dc, applicationVolume);
		VolumeUtils.createVolumeIfRequired(dc, outputVolume);
		VolumeUtils.createVolumeIfRequired(dc, envVolume);
		
		//configure our call to 'creator' which will do all the work.
		String[] args = {
			"/cnb/lifecycle/creator",
			"-uid", ""+userId,
			"-gid", ""+groupId,
			"-cache-dir", BUILD_VOL_PATH,
			"-app", APP_VOL_PATH+"/content",
			"-layers", OUTPUT_VOL_PATH,
			"-platform", ENV_VOL_PATH,
			"-run-image", runImage,		
			"-launch-cache", LAUNCH_VOL_PATH,			
			"-daemon",		
			"-log-level", "debug",
			"-skip-restore",
			finalImage
		};
		
		//TODO: read metadata from buildImage to confirm lifecycle version/platform version compatibility.
				
		//TODO: add labels for container for creator etc (as per spec)
		
		//create a container using buildImage that will invoke the creator process
		String id = ContainerUtils.createContainer(dc, buildImage, Arrays.asList(args),
				new VolumeBind(buildCacheVolume,BUILD_VOL_PATH),
				new VolumeBind(launchCacheVolume,LAUNCH_VOL_PATH),
				new VolumeBind(applicationVolume,APP_VOL_PATH),				
				new VolumeBind("/var/run/docker.sock","/var/run/docker.sock"),
				new VolumeBind(outputVolume,OUTPUT_VOL_PATH)
				);

		//TODO: add environment volume content from caller (add to method args)
		//VolumeUtils.addContentToVolume(dc, envVolume, "env/CNB_SKIP_LAYERS", "true");
		
		//add the application to the container. Note we are placing it at /app/content, because 
		//the /app mountpoint is mounted such that the user has no perms to create new content there,
		//but subdirs are ok. 
		ContainerUtils.addContentToContainer(dc, id, APP_VOL_PATH+"/content", userId, groupId, appDir);
		
		//launch the container! 
		dc.startContainerCmd(id).exec();
		
		//grab the logs to stdout.
		dc.logContainerCmd(id)
		  .withFollowStream(true)
		  .withStdOut(true)
		  .withStdErr(true)
		  .withTimestamps(true)
		  .exec(new StdOutContainerLog());
		
		//wait for the container to complete, and retrieve the exit code.
		int rc = dc.waitContainerCmd(id).exec(new WaitContainerResultCallback()).awaitStatusCode();
		System.out.println("Build exited with code "+rc);
		
		//tidy up. remove container.
		ContainerUtils.removeContainer(dc, id);
		
		//remove volumes 
		//(note when/if we persist the cache between builds, we'll be more selective here over what we remove)
		VolumeUtils.removeVolume(dc, buildCacheVolume);
		VolumeUtils.removeVolume(dc, launchCacheVolume);
		VolumeUtils.removeVolume(dc, applicationVolume);
		VolumeUtils.removeVolume(dc, outputVolume);
		VolumeUtils.removeVolume(dc, envVolume);
	}
	
	//util method for random suffix.
	private String randomString(int length) {
		return (new Random()).ints('a', 'z'+1).limit(length).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
	}
}

