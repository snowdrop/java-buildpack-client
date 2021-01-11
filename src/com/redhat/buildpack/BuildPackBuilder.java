package com.redhat.buildpack;

import com.redhat.buildpack.docker.ContainerEntry;

import java.io.File;
import java.io.InputStream;
import java.util.Map;

public interface BuildPackBuilder {

    static BuildPackBuilder get() {
        return new BuildPackBuilderImpl();
    }

    BuildPackBuilder fromBuilder(BuildPackBuilder builder);

    BuildPackBuilder withRunImage(String image);

    BuildPackBuilder withBuildImage(String image);

    BuildPackBuilder withEnv(String key, String value);

    BuildPackBuilder withEnv(Map<String, String> environment);

    BuildPackBuilder withDockerHost(String dockerHost);

    BuildPackBuilder withContent(String prefix, File content) throws Exception;

    BuildPackBuilder withContent(String filepath, String filecontent);

    BuildPackBuilder withContent(String filepath, long length, InputStream filecontent) throws Exception;

    BuildPackBuilder withContent(ContainerEntry... entries) throws Exception;


    BuildPackBuilder useDockerDaemon(boolean useDaemon);

    BuildPackBuilder withBuildCache(String cacheVolume);

    BuildPackBuilder removeBuildCacheAfterBuild(boolean remove);

    BuildPackBuilder withLaunchCache(String cacheVolume);

    BuildPackBuilder removeLaunchCacheAfterBuild(boolean remove);

    BuildPackBuilder withLogLevel(String logLevel);

    BuildPackBuilder requestBuildTimestamps(boolean timestampsEnabled);

    BuildPackBuilder withFinalImage(String image);

    int build() throws Exception;

    int build(LogReader logger) throws Exception;

    static interface LogReader {
        public boolean stripAnsiColor();

        public void stdout(String message);

        public void stderr(String message);
    }

}