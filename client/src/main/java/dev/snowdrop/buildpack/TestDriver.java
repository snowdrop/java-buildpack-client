package dev.snowdrop.buildpack;

import java.io.File;
import java.util.HashMap;

public class TestDriver {

  public TestDriver() throws Exception {

    //run this with 
    // mvnw exec:java -Dexec.classpathScope="test"  -Dexec.mainClass="dev.snowdrop.buildpack.TestDriver"

    System.setProperty("org.slf4j.simpleLogger.log.dev.snowdrop.buildpack","debug");
    System.setProperty("org.slf4j.simpleLogger.log.dev.snowdrop.buildpack.docker","debug");
    System.setProperty("org.slf4j.simpleLogger.log.dev.snowdrop.buildpack.phases","debug");

    HashMap<String,String> env = new HashMap<>();
    //env.put("CNB_USER_ID","0");

    Buildpack.builder()
      .addNewFileContent(new File("/home/sample-springboot-java-app/"))
      .withBuilderImage("localhost:5000/paketobuildpacks/builder:base")
      .withFinalImage("testdriver/testimage:latest")
      .withUseDaemon(true)
      .withDockerNetwork("host")
      .withLogger(new SystemLogger())
      .withLogLevel("debug")
      .withEnvironment(env)
      .build();
  }

  public static void main(String[] args) throws Exception {
    @SuppressWarnings("unused")
    TestDriver td = new TestDriver();
  }
}