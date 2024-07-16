///usr/bin/env jbang "$0" "$@" ; exit $?

//REPOS mavencentral,jitpack
//DEPS org.slf4j:slf4j-simple:1.7.30
//DEPS ${env.CURRENT_WORKFLOW_DEP:dev.snowdrop:buildpack-client:0.0.10}


import java.io.File;
import dev.snowdrop.buildpack.*;
import dev.snowdrop.buildpack.config.*;
import dev.snowdrop.buildpack.docker.*;

public class pack {

    public static void main(String... args) {

      System.setProperty("org.slf4j.simpleLogger.log.dev.snowdrop.buildpack","debug");
      System.setProperty("org.slf4j.simpleLogger.log.dev.snowdrop.buildpack.docker","debug");
      System.setProperty("org.slf4j.simpleLogger.log.dev.snowdrop.buildpack.lifecycle","debug");
      System.setProperty("org.slf4j.simpleLogger.log.dev.snowdrop.buildpack.lifecycle.phases","debug");      

      int exitCode = BuildConfig.builder()
                           .withBuilderImage(new ImageReference("docker.io/paketocommunity/builder-ubi-base"))
                           .withOutputImage(new ImageReference("snowdrop/hello-quarkus"))
                           .withNewLogConfig()
                              .withLogger(new SystemLogger())
                              .withLogLevel("debug")
                           .and()                           
                           .addNewFileContentApplication(new File("."))
                           .build()
                           .getExitCode();

      System.exit(exitCode);
    }
}
