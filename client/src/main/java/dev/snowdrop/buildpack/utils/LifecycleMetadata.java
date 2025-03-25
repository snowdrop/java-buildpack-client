package dev.snowdrop.buildpack.utils;

import java.util.List;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.snowdrop.buildpack.BuildpackException;
import dev.snowdrop.buildpack.config.DockerConfig;
import dev.snowdrop.buildpack.config.ImageReference;
import dev.snowdrop.buildpack.docker.ImageUtils;
import dev.snowdrop.buildpack.docker.ImageUtils.ImageInfo;

public class LifecycleMetadata {

    private List<String> platformLevels;
    private List<String> buildpackLevels;

    public LifecycleMetadata(DockerConfig dc, ImageReference lifecycleImage) throws BuildpackException {

        // pull and inspect the builderImage to obtain builder metadata.
        ImageUtils.pullImages(dc,lifecycleImage);

        ImageInfo ii = ImageUtils.inspectImage(dc.getDockerClient(), lifecycleImage);

        String metadataJson = ii.labels.get("io.buildpacks.lifecycle.apis");

        //dig into metadata for supported platforms.
        ObjectMapper om = new ObjectMapper();
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            JsonNode root = om.readTree(metadataJson);

            platformLevels = JsonUtils.getArray(root, "/platform/supported");
            // if supported platform metadata is unparseable.
            if(platformLevels==null){
                throw new Exception("Bad platform metadata in lifecycle image");
            }

            buildpackLevels = JsonUtils.getArray(root, "/buildpack/supported");
            // if supported platform metadata is unparseable.
            if(buildpackLevels==null){
                throw new Exception("Bad buildpack metadata in lifecycle image");
            }            

        } catch (Exception e) {
            throw BuildpackException.launderThrowable(e);
        }
    }

    public List<String> getSupportedPlatformLevels(){
        return platformLevels;
    }
    public List<String> getSupportedBuildpackLevels(){
        return buildpackLevels;
    }
}
