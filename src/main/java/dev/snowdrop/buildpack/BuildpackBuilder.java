package dev.snowdrop.buildpack;

import java.io.File;
import java.util.Map;

import dev.snowdrop.buildpack.docker.ContainerEntry;
import dev.snowdrop.buildpack.docker.ContainerEntry.ContentSupplier;

public interface BuildpackBuilder {
    
    //obtain instance.
    static BuildpackBuilder get() {
        return new BuildpackBuilderImpl();
    }

    //copy a builder config. 
    BuildpackBuilder fromBuilder(BuildpackBuilder builder);

    //run/build/final image names.
    BuildpackBuilder withRunImage(String image);
    BuildpackBuilder withBuildImage(String image);
    BuildpackBuilder withFinalImage(String image);   
    
    //timeout when pulling images
    BuildpackBuilder withPullTimeout(int seconds);

    //env during build (TODO!)
    BuildpackBuilder withEnv(String key, String value);
    BuildpackBuilder withEnv(Map<String, String> environment);

    //dockerhost override
    BuildpackBuilder withDockerHost(String dockerHost);
    //use registry, not daemon (TODO!)
    BuildpackBuilder useDockerDaemon(boolean useDaemon);    

    //application content specification
    BuildpackBuilder withContent(File content) throws Exception;    
    BuildpackBuilder withContent(String prefix, File content) throws Exception;
    BuildpackBuilder withContent(String filepath, String filecontent);
    BuildpackBuilder withContent(String filepath, long length, ContentSupplier content) throws Exception;
    BuildpackBuilder withContent(ContainerEntry... entries) throws Exception;

    //build cache configuration
    BuildpackBuilder withBuildCache(String cacheVolume);
    BuildpackBuilder removeBuildCacheAfterBuild(boolean remove);

    //launch cache configuration
    BuildpackBuilder withLaunchCache(String cacheVolume);
    BuildpackBuilder removeLaunchCacheAfterBuild(boolean remove);

    //build message output configuration
    BuildpackBuilder withLogLevel(String logLevel); //(debug|info|warn|warning)
    BuildpackBuilder requestBuildTimestamps(boolean timestampsEnabled);

    //execute build
    int build() throws Exception;
    int build(LogReader logger) throws Exception;

    //handle messages during build
    static interface LogReader {
        public boolean stripAnsiColor();
        public void stdout(String message);
        public void stderr(String message);
    }

}