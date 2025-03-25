///usr/bin/env jbang "$0" "$@" ; exit $?

//REPOS mavencentral,jitpack
//DEPS org.slf4j:slf4j-simple:1.7.30
//DEPS ${env.CURRENT_WORKFLOW_DEP:dev.snowdrop:buildpack-client:0.0.13-SNAPSHOT}


import java.io.File;
import dev.snowdrop.buildpack.*;
import dev.snowdrop.buildpack.config.*;
import dev.snowdrop.buildpack.docker.*;
import java.util.Map;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.NotFoundException;

public class pack {

    public static void main(String... args) {

      System.setProperty("org.slf4j.simpleLogger.log.dev.snowdrop.buildpack","debug");
      System.setProperty("org.slf4j.simpleLogger.log.dev.snowdrop.buildpack.docker","debug");
      System.setProperty("org.slf4j.simpleLogger.log.dev.snowdrop.buildpack.lifecycle","debug");
      System.setProperty("org.slf4j.simpleLogger.log.dev.snowdrop.buildpack.lifecycle.phases","debug");

      String debugScript = "#!/bin/bash\n" +
      "echo \"DEBUG INFO\"\n" +
      "echo \"Root Perms\"\n" +
      "stat -c \"%A $a %u %g %n\" /*\n" +
      "echo \"Layer dir Content\"\n" +
      "ls -lar /layers\n" +
      "echo \"Workspace dir Content\"\n" +
      "ls -lar /workspace\n" +
      "echo \"Analyzed toml\"\n" +
      "ls -la /layers\n" +       
      "cat /layers/analyzed.toml\n" +        
      "LC=$1\n" +
      "shift\n" +
      "$LC \"$@\"";

      String JDK="17";

      Map<String,String> envMap = new java.util.HashMap<>();
      envMap.put("BP_JVM_VERSION",JDK);

      int exitCode = BuildConfig.builder()
                           //.withBuilderImage(new ImageReference("docker.io/paketocommunity/builder-ubi-base"))
                           .withBuilderImage(new ImageReference("quay.io/ozzydweller/testbuilders:paketo-default"))
                           .withOutputImage(new ImageReference("snowdrop/hello-quarkus:jvm"+JDK))
                           .withNewDockerConfig()
                              .withPullPolicy(DockerConfig.PullPolicy.IF_NOT_PRESENT)
                           .and()
                           .withNewPlatformConfig()
                              .withPhaseDebugScript(debugScript)
                              .withEnvironment(envMap)
                           .and()
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
