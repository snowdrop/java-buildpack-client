package dev.snowdrop.buildpack;

import java.io.File;
import java.util.HashMap;

import dev.snowdrop.buildpack.config.ImageReference;

public class TestDriver {

  public static void main(String[] args) throws Exception {

    System.setProperty("org.slf4j.simpleLogger.log.dev.snowdrop.buildpack","debug");
    System.setProperty("org.slf4j.simpleLogger.log.dev.snowdrop.buildpack.docker","debug");
    System.setProperty("org.slf4j.simpleLogger.log.dev.snowdrop.buildpack.lifecycle","debug");
    System.setProperty("org.slf4j.simpleLogger.log.dev.snowdrop.buildpack.lifecycle.phases","debug");

    HashMap<String,String> env = new HashMap<>();

    
    int exitCode = 
    BuildConfig.builder().withNewDockerConfig()
                            .withUseDaemon(false)
                            .withDockerNetwork("host")
                            .and()
                         .withNewLogConfig()
                            .withLogger(new SystemLogger())
                            .withLogLevel("debug")
                            .and()
                         .withNewKanikoCacheConfig()
                            //.withCacheVolumeName("mykanikocache")
                            .withDeleteCacheAfterBuild(true)
                            .and()
                         .withNewBuildCacheConfig()
                            //.withCacheVolumeName("buildcachevol")
                            .withDeleteCacheAfterBuild(true)
                            .and()
                         .withNewLaunchCacheConfig()
                            //.withCacheVolumeName("launchcachevol")
                            .withDeleteCacheAfterBuild(true)
                            .and()
                         .withNewPlatformConfig()
                            .withEnvironment(env)
                            .withTrustBuilder(false)
                            .and()
                         .withBuilderImage(new ImageReference("paketocommunity/builder-ubi-base"))
                         .withOutputImage(new ImageReference("localhost:5000/testdriver/newimage6:latest"))
                         .addNewFileContentApplication(new File("/tmp/sample-springboot-java-app/"))
                         .build()
                         .getExitCode();

    System.out.println("Build for completed with exit code "+exitCode);
    
  }
}