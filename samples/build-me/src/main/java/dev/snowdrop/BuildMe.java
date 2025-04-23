package dev.snowdrop;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import dev.snowdrop.buildpack.*;
import dev.snowdrop.buildpack.config.*;

public class BuildMe {
        
    public static void main(String... args) {

        System.setProperty("org.slf4j.simpleLogger.log.dev.snowdrop.buildpack","debug");
        System.setProperty("org.slf4j.simpleLogger.log.dev.snowdrop.buildpack.docker","debug");
        System.setProperty("org.slf4j.simpleLogger.log.dev.snowdrop.buildpack.lifecycle","debug");
        System.setProperty("org.slf4j.simpleLogger.log.dev.snowdrop.buildpack.lifecycle.phases","debug");

        String IMAGE_REF = Optional.ofNullable(System.getenv("IMAGE_REF"))
            .orElseThrow(() -> new IllegalStateException("Missing env var: PROJECT_PATH"));
        String PROJECT_PATH = Optional.ofNullable(System.getenv("PROJECT_PATH"))
            .orElseThrow(() -> new IllegalStateException("Missing env var: PROJECT_PATH"));

        Map<String, String> envMap = System.getenv().entrySet().stream()
            .filter(entry -> entry.getKey().startsWith("BP_") || entry.getKey().startsWith("CNB_"))
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (oldValue, newValue) -> newValue,
                HashMap::new
            ));

        List<RegistryAuthConfig> authInfo = new ArrayList<>();
        if(System.getenv("REGISTRY_ADDRESS")!=null){
          String registry = System.getenv("REGISTRY_SERVER");
          String username = System.getenv("REGISTRY_USER");
          String password = System.getenv("REGISTRY_PASS");
          RegistryAuthConfig authConfig = RegistryAuthConfig.builder()
                                                .withRegistryAddress(registry)
                                                .withUsername(username)
                                                .withPassword(password)
                                                .build();
          authInfo.add(authConfig);
        }

        int exitCode = BuildConfig.builder()
            .withBuilderImage(new ImageReference("paketocommunity/builder-ubi-base:latest"))
            .withOutputImage(new ImageReference(IMAGE_REF))
            .withNewPlatformConfig()
              .withEnvironment(envMap)
            .endPlatformConfig()
            .withNewDockerConfig()
              .withAuthConfigs(authInfo)
              .withUseDaemon(true)
            .endDockerConfig()
            .withNewLogConfig()
              .withLogger(new SystemLogger())
              .withLogLevel("debug")
            .and()
            .addNewFileContentApplication(new File(PROJECT_PATH))
            .build()
            .getExitCode();

        System.exit(exitCode);
    }
}