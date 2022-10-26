package dev.snowdrop.buildpack.utils;

import dev.snowdrop.buildpack.BuildpackException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class BuildpackMetadata {
    public static String getRunImageFromMetadata(String json, String runImage) throws BuildpackException {

        // if caller set runImage, that choice overrides metadata.
        if(runImage!=null)
            return runImage;

        // else, dig into metadata for runImage.
        ObjectMapper om = new ObjectMapper();
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            JsonNode root = om.readTree(json);
            // read the buildpacks recommended runImage
            String ri = JsonUtils.getValue(root, "stack/runImage/image");

            // if caller did not set runImage, and metadata is absent, error.
            if(ri==null){
                throw new Exception("No runImage specified, and builderImage is missing metadata declaration");
            }

            // remap docker.io references back to docker.io
            if (ri.startsWith("index.docker.io/")) {
                ri = ri.substring("index.docker.io/".length());
                ri = "docker.io/" + ri;
            }
            
            return ri;
        } catch (Exception e) {
            throw BuildpackException.launderThrowable(e);
        }
    }
}
