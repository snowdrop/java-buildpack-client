package com.redhat.buildpack;

import java.io.File;
import java.io.IOException;

import com.github.dockerjava.api.DockerClient;
import com.redhat.buildpack.docker.VolumeUtils;

public class TestDriver {
		
	public TestDriver() throws Exception {

		BuildPackBuilder.get()
			.withContent(new File("/home/ozzy/Work/java-buildpack-client"))
			.withFinalImage("test/testimage:latest")
			.build();

	}
		
	public static void main(String[] args) throws Exception {
		TestDriver td = new TestDriver();
	}
}
