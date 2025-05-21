package dev.snowdrop.buildpack.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import dev.snowdrop.buildpack.docker.DockerClientUtils;
import io.sundr.builder.annotations.Buildable;

@Buildable(generateBuilderPackage=true, builderPackage="dev.snowdrop.buildpack.builder")
public class HostAndSocketConfig {
    private String host;
    private String socket;

    public static HostAndSocketConfigBuilder builder() {
        return new HostAndSocketConfigBuilder();
    }   

    public HostAndSocketConfig(String host,
                               String socket) {
        this.host = host;
        this.socket = socket;
    }

    public Optional<String> getHost() {
        return Optional.ofNullable(host);
    }
    public Optional<String> getSocket() {
        return Optional.ofNullable(socket);
    }
}
