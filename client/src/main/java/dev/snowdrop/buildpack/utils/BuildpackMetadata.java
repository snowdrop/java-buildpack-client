package dev.snowdrop.buildpack.utils;

import java.util.ArrayList;
import java.util.List;

import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.snowdrop.buildpack.BuildpackException;
import dev.snowdrop.buildpack.docker.ContainerUtils;

public class BuildpackMetadata {

    //used for builders created for platform 0.12 onwards
    public static List<String> getRunImageFromRunTOML(String imageReference) throws BuildpackException {
        List<String> runImages = new ArrayList<>();

        byte[] runTomlData = ContainerUtils.getFileFromContainer(null, imageReference, "/cnb/run.toml");
        TomlParseResult analyzed = Toml.parse(new String(runTomlData));

        TomlArray ta = analyzed.getArray("images");
        for(int i=0; i<ta.size(); i++){
            runImages.add(ta.getTable(i).getString("image"));
        }
        return runImages;
    }

    //used for platform before 0.12
    public static String getRunImageFromMetadataJSON(String json) throws BuildpackException {
        //dig into metadata for runImage.
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

    public static List<String> getSupportedPlatformsFromMetadata(String json) throws BuildpackException {
        //dig into metadata for supported platforms.
        ObjectMapper om = new ObjectMapper();
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            JsonNode root = om.readTree(json);

            List<String> platforms = JsonUtils.getArray(root, "lifecycle/apis/platform/supported");

            // if supported platform metadata is unparseable.
            if(platforms==null){
                throw new Exception("Bad platform metadata in builder image");
            }
            
            return platforms;
        } catch (Exception e) {
            throw BuildpackException.launderThrowable(e);
        }
    }
}
