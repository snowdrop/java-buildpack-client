///usr/bin/env jbang "$0" "$@" ; exit $?

//REPOS mavencentral,jitpack
//DEPS org.slf4j:slf4j-simple:1.7.30
//DEPS ${env.CURRENT_WORKFLOW_DEP}


import java.io.File;
import dev.snowdrop.buildpack.*;
import dev.snowdrop.buildpack.config.*;
import dev.snowdrop.buildpack.docker.*;
import dev.snowdrop.buildpack.utils.OperatingSytem;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.List;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.NotFoundException;

public class RunRegistryTest {

    public static void main(String... args) {
        try{
            run();
        }catch(Exception e){
            System.err.println("Error during run...");
            e.printStackTrace();
            System.exit(250);
        }
    }

    private static void run() throws Exception {

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
      "cat /layers/analyzed.toml\n" +
      "echo \"Run toml\"\n" +
      "cat /cnb/run.toml\n" +
      "echo \"Stack toml\"\n" +
      "cat /cnb/stack.toml\n" +
      "echo \"DEBUG END\"\n" +    
      "LC=$1\n" +
      "shift\n" +
      "$LC \"$@\"";

      String projectPath = Optional.ofNullable(System.getenv("PROJECT_PATH")).orElse(".");
      String JDK = Optional.ofNullable(System.getenv("JDK")).orElse("17");
      String builderImage = Optional.ofNullable(System.getenv("BUILDER_IMAGE")).orElse("quay.io/ozzydweller/testbuilders:debug-exporter");
      String outputImage = Optional.ofNullable(System.getenv("OUTPUT_IMAGE")).orElse("snowdrop/hello-quarkus:jvm"+JDK);

      System.out.println("RunTest Building path '"+projectPath+"' using '"+builderImage+"' requesting jdk '"+JDK+"'");

      Map<String,String> envMap = new HashMap<>();
      envMap.put("BP_JVM_VERSION",JDK);

      List<RegistryAuthConfig> authInfo = new ArrayList<>();

      if(System.getenv("REGISTRY_ADDRESS")!=null){
        String registry = System.getenv("REGISTRY_ADDRESS");
        String username = System.getenv("REGISTRY_USER");
        String password = System.getenv("REGISTRY_PASS");
        RegistryAuthConfig authConfig = RegistryAuthConfig.builder()
                                              .withRegistryAddress(registry)
                                              .withUsername(username)
                                              .withPassword(password)
                                              .build();
        authInfo.add(authConfig);
      }
         
      int exitCode = 0;

      OperatingSytem os = OperatingSytem.getOperationSystem();
      if(os != OperatingSytem.WIN) {
          System.out.println("Building "+outputImage+" using "+authInfo.size()+" credentials, with builder "+builderImage);
          exitCode = BuildConfig.builder()
                           .withBuilderImage(new ImageReference(builderImage))
                           .withOutputImage(new ImageReference(outputImage))
                           .withNewDockerConfig()
                              .withAuthConfigs(authInfo)
                              .withUseDaemon(false)
                           .and()
                           .withNewPlatformConfig()
                              .withEnvironment(envMap)
                              //.withPlatformLevel("0.12")
                              //.withPhaseDebugScript(debugScript)
                           .and()
                           .withNewLogConfig()
                              .withLogger(new SystemLogger())
                              //.withLogLevel("debug")
                           .and()                           
                           .addNewFileContentApplication(new File(projectPath))
                           .build()
                           .getExitCode();
      }else{
          //github windows runner cannot run linux docker containers, 
          //so we'll just test the ability for the library to correctly talk
          //to the docker daemon.
          DockerClient dc = DockerClientUtils.getDockerClient();
          try{
            dc.pingCmd().exec();
          }catch(Exception e){
            throw new RuntimeException("Unable to verify docker settings", e);
          }
      }

      System.exit(exitCode);
    }
}