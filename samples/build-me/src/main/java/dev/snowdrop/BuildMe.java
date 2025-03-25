package dev.snowdrop;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import dev.snowdrop.buildpack.*;
import dev.snowdrop.buildpack.config.*;
import dev.snowdrop.buildpack.docker.*;
import dev.snowdrop.buildpack.utils.OperatingSytem;

import static dev.snowdrop.buildpack.docker.DockerClientUtils.getDockerClient;

public class BuildMe {
        
    public static void main(String... args) {

        System.setProperty("org.slf4j.simpleLogger.log.dev.snowdrop.buildpack","debug");
        System.setProperty("org.slf4j.simpleLogger.log.dev.snowdrop.buildpack.docker","debug");
        System.setProperty("org.slf4j.simpleLogger.log.dev.snowdrop.buildpack.lifecycle","debug");
        System.setProperty("org.slf4j.simpleLogger.log.dev.snowdrop.buildpack.lifecycle.phases","debug");

        String REGISTRY_USERNAME = System.getenv("REGISTRY_USERNAME");
        String REGISTRY_PASSWORD = System.getenv("REGISTRY_PASSWORD");
        String REGISTRY_SERVER = System.getenv("REGISTRY_SERVER");
        String IMAGE_REF = System.getenv("IMAGE_REF");
        String PROJECT_PATH = System.getenv("PROJECT_PATH");

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
              .withUseDaemon(false)
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