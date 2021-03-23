package dev.snowdrop.buildpack;

import java.io.File;

public class TestDriver {

  public TestDriver() throws Exception {
    BuildpackBuilder.get()
      .withContent(new File("/home/ozzy/Work/java-buildpack-client"))
      .withFinalImage("test/testimage:latest")
      .withLogLevel("debug")
      .build();
  }

  public static void main(String[] args) throws Exception {
    TestDriver td = new TestDriver();
  }
}
