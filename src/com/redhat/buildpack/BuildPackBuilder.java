package com.redhat.buildpack;

import com.redhat.buildpack.docker.ContainerEntry;

import java.io.File;
import java.io.InputStream;
import java.util.Map;

public interface BuildPackBuilder {
    
    //obtain instance.
    static BuildPackBuilder get() {
        return new BuildPackBuilderImpl();
    }

    //copy a builder config. 
    BuildPackBuilder fromBuilder(BuildPackBuilder builder);

    //run/build/final image names.
    BuildPackBuilder withRunImage(String image);
    BuildPackBuilder withBuildImage(String image);
    BuildPackBuilder withFinalImage(String image);   
    
    //timeout when pulling images
    BuildPackBuilder withPullTimeout(int seconds);

    //env during build (TODO!)
    BuildPackBuilder withEnv(String key, String value);
    BuildPackBuilder withEnv(Map<String, String> environment);

    //dockerhost override
    BuildPackBuilder withDockerHost(String dockerHost);
    //use registry, not daemon (TODO!)
    BuildPackBuilder useDockerDaemon(boolean useDaemon);    

    //application content specification
    BuildPackBuilder withContent(File content) throws Exception;    
    BuildPackBuilder withContent(String prefix, File content) throws Exception;
    BuildPackBuilder withContent(String filepath, String filecontent);
    BuildPackBuilder withContent(String filepath, long length, InputStream filecontent) throws Exception;
    BuildPackBuilder withContent(ContainerEntry... entries) throws Exception;

    //build cache configuration
    BuildPackBuilder withBuildCache(String cacheVolume);
    BuildPackBuilder removeBuildCacheAfterBuild(boolean remove);

    //launch cache configuration
    BuildPackBuilder withLaunchCache(String cacheVolume);
    BuildPackBuilder removeLaunchCacheAfterBuild(boolean remove);

    //build message output configuration
    BuildPackBuilder withLogLevel(String logLevel);
    BuildPackBuilder requestBuildTimestamps(boolean timestampsEnabled);

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