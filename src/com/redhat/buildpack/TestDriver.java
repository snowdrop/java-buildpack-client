package com.redhat.buildpack;

import java.io.File;
import java.io.IOException;

import com.github.dockerjava.api.DockerClient;
import com.redhat.buildpack.docker.VolumeUtils;

public class TestDriver {
		
	public TestDriver() throws Exception {
		BuildPackBuilder bp = new BuildPackBuilder(
				//buildImage to use.
				"gcr.io/paketo-buildpacks/builder:base-platform-api-0.3",
				//runImage will be obtained from buildImage metadata.	
				null, 
				//targetImage to create
				"test/testimage:latest",
				//projectSource location.
				new File("C:\\git\\demo-project")
				);

		//pull build image, read metadata etc.				
		bp.prep();
		//execute build.
		bp.build();
	}
		
	public static void main(String[] args) throws Exception {
		TestDriver td = new TestDriver();
	}
}
