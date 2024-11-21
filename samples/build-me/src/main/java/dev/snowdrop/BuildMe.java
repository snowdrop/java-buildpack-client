package dev.snowdrop;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.github.dockerjava.api.DockerClient;
import dev.snowdrop.buildpack.*;
import dev.snowdrop.buildpack.config.*;

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
        String DOCKER_HOST = System.getenv("DOCKER_HOST");

        Map<String, String> envMap = System.getenv().entrySet().stream()
            .filter(entry -> entry.getKey().startsWith("BP_") || entry.getKey().startsWith("CNB_"))
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (oldValue, newValue) -> newValue,
                HashMap::new
            ));

        DockerClient client = getDockerClient();
        client.authConfig()
            .withUsername(REGISTRY_USERNAME)
            .withPassword(REGISTRY_PASSWORD)
            .withRegistryAddress(REGISTRY_SERVER);

        int exitCode = BuildConfig.builder()
            .withBuilderImage(new ImageReference("paketocommunity/builder-ubi-base:latest"))
            .withOutputImage(new ImageReference(IMAGE_REF))
            .withNewPlatformConfig()
              .withEnvironment(envMap)
            .endPlatformConfig()
            .withNewDockerConfig()
              .withDockerClient(client)
              .withDockerHost(DOCKER_HOST)
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