package dev.snowdrop;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

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

        String PROJECT_PATH = System.getenv("PROJECT_PATH");
        File filePath = new File(PROJECT_PATH);

        Map<String, String> envMap = new HashMap<>();
        envMap.put("BP_JVM_VERSION", "21");
        // envMap.put("BP_MAVEN_BUILT_ARTIFACT","target/quarkus-app/lib/ target/quarkus-app/*.jar target/quarkus-app/app/ target/quarkus-app/quarkus");
        // envMap.put("CNB_LOG_LEVEL","trace");

        DockerClient client = getDockerClient();
        client.authConfig()
            .withUsername(REGISTRY_USERNAME)
            .withPassword(REGISTRY_PASSWORD)
            .withRegistryAddress(REGISTRY_SERVER);

        int exitCode = BuildConfig.builder()
            .withBuilderImage(new ImageReference("paketocommunity/builder-ubi-base:latest"))
            .withOutputImage(new ImageReference("quay.io/snowdrop/my-quarkus-app"))
            .withNewPlatformConfig()
              .withEnvironment(envMap)
            .endPlatformConfig()
            .withNewDockerConfig()
              .withDockerClient(client)
            .endDockerConfig()
            .withNewLogConfig()
              .withLogger(new SystemLogger())
              .withLogLevel("debug")
            .and()
            .addNewFileContentApplication(filePath)
            .build()
            .getExitCode();

        System.exit(exitCode);
    }
}