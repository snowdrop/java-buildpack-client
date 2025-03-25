package dev.snowdrop.buildpack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;

import dev.snowdrop.buildpack.config.DockerConfig;
import dev.snowdrop.buildpack.config.ImageReference;
import dev.snowdrop.buildpack.config.PlatformConfig;
import dev.snowdrop.buildpack.docker.ContainerUtils;
import dev.snowdrop.buildpack.docker.ImageUtils;
import dev.snowdrop.buildpack.docker.ImageUtils.ImageInfo;
import dev.snowdrop.buildpack.lifecycle.Version;
import dev.snowdrop.buildpack.utils.JsonUtils;

public class BuilderImage {

    private int DEFAULT_USER_ID = 1000;
    private int DEFAULT_GROUP_ID = 1000;

    private ImageReference image;

    private int userId;
    private int groupId;

    private boolean hasExtensions;

    private String metadataJson;
    private ImageReference runImage;
    private List<ImageReference> runImages;

    private List<String> builderSupportedPlatforms;

    //image os/arch.. not buildpack platform!
    private String platform;

    private DockerClient dc;

    public BuilderImage(BuilderImage original, boolean addedExtensions, ImageReference extended) {
        this.userId = original.userId;
        this.groupId = original.groupId;
        this.runImages = original.runImages;
        this.builderSupportedPlatforms = original.builderSupportedPlatforms;
        this.hasExtensions = original.hasExtensions || addedExtensions;
        this.image = extended;
        this.platform = original.platform;
        this.dc = original.dc;
    }

    public BuilderImage(DockerConfig dc, PlatformConfig pc, ImageReference runImage, ImageReference builderImage){
        image = builderImage;
        this.dc = dc.getDockerClient();

        // pull and inspect the builderImage to obtain builder metadata.
        ImageUtils.pullImages(dc, builderImage);

        ImageInfo ii = ImageUtils.inspectImage(dc.getDockerClient(), builderImage);

        //grab the os/arch platform from the image.. 
        this.platform = ii.platform;

        // read the userid/groupid for the buildpack from it's env.
        userId = DEFAULT_USER_ID;
        groupId = DEFAULT_GROUP_ID;
        for (String s : ii.env) {
            if (s.startsWith("CNB_USER_ID=")) {
                userId = Integer.valueOf(s.substring("CNB_USER_ID=".length()));
            }
            if (s.startsWith("CNB_GROUP_ID=")) {
                groupId = Integer.valueOf(s.substring("CNB_GROUP_ID=".length()));
            }
        }
        // override userid/groupid if cnb vars present within environment
        if(pc.getEnvironment().containsKey("CNB_USER_ID")) { 
            userId = Integer.valueOf(pc.getEnvironment().get("CNB_USER_ID")); 
        }
        if(pc.getEnvironment().containsKey("CNB_GROUP_ID")) { 
            userId = Integer.valueOf(pc.getEnvironment().get("CNB_GROUP_ID")); 
        }

        String xtnLayers = ii.labels.get("io.buildpacks.extension.layers");
        //if xtnLayers is absent, or is just the empty json {}, there are no extensions.
        if(xtnLayers!=null && !xtnLayers.isEmpty()){
            xtnLayers = xtnLayers.trim();
            xtnLayers.replaceAll("\\\\w", "");
            if(xtnLayers.equals("{}")){
                xtnLayers = null;
            }
        }
        hasExtensions = (xtnLayers!=null && !xtnLayers.isEmpty());
        
        //defer the calculation of run images to the getter, because we 
        //need to know the selected platform level to know how to find the run metadata.
        this.metadataJson = ii.labels.get("io.buildpacks.builder.metadata"); 
        this.runImage = runImage; 
        this.runImages = null;

        builderSupportedPlatforms = getSupportedPlatformsFromMetadata();
    }

    public ImageReference getImage(){
        return image;
    }
    public int getUserId() {
        return userId;
    }
    public int getGroupId() {
        return groupId;
    }
    public boolean hasExtensions() {
        return hasExtensions;
    }
    public ImageReference[] getRunImages(Version activePlatformLevel) {
        if(runImages==null){
            runImages = new ArrayList<>();
            if(runImage!=null){
                // use user specified run image
                runImages.add(runImage);
            }else{
                Set<ImageReference> runSet = new HashSet<>();
                if(activePlatformLevel.atLeast("0.12")){
                    runSet.addAll(getRunImageFromRunTOML());
                }else{
                    // obtain the buildpack metadata json identified runImage
                    runSet.add(getRunImageFromMetadataJSON());
                }
                runImages.addAll(runSet);
            }
        }
        return runImages.toArray(new ImageReference[]{});
    }
    public void setRunImage(ImageReference runImage){
        runImages = new ArrayList<ImageReference>();
        runImages.add(runImage);
    }
    public List<String> getBuilderSupportedPlatforms() {
        return builderSupportedPlatforms;
    }
    public String getImagePlatform(){
        return platform;
    }


    //used for builders created for platform 0.12 onwards
    private List<ImageReference> getRunImageFromRunTOML() throws BuildpackException {
        List<ImageReference> runImages = new ArrayList<>();

        List<String> command = Stream.of("").collect(Collectors.toList());
        String builderContainerId = ContainerUtils.createContainer(dc, image.getCanonicalReference(), command);
        byte[] runTomlData = new byte[0];
        try{
            runTomlData = ContainerUtils.getFileFromContainer(dc, builderContainerId, "/cnb/run.toml");
        }finally{
            ContainerUtils.removeContainer(dc, builderContainerId);
        }

        TomlParseResult analyzed = Toml.parse(new String(runTomlData));

        TomlArray ta = analyzed.getArray("images");
        for(int i=0; i<ta.size(); i++){
            runImages.add(new ImageReference(ta.getTable(i).getString("image")));
        }
        return runImages;
    }

    //used for platform before 0.12
    private ImageReference getRunImageFromMetadataJSON() throws BuildpackException {
        //dig into metadata for runImage.
        ObjectMapper om = new ObjectMapper();
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            JsonNode root = om.readTree(this.metadataJson);
            // read the buildpacks recommended runImage
            String ri = JsonUtils.getValue(root, "stack/runImage/image");

            // if caller did not set runImage, and metadata is absent, error.
            if(ri==null){
                throw new Exception("No runImage specified, and builderImage is missing metadata declaration");
            }

            // remap docker.io references back to docker.io
            // if (ri.startsWith("index.docker.io/")) {
            //     ri = ri.substring("index.docker.io/".length());
            //     ri = "docker.io/" + ri;
            // }
            
            return new ImageReference(ri);
        } catch (Exception e) {
            throw BuildpackException.launderThrowable(e);
        }
    }

    private List<String> getSupportedPlatformsFromMetadata() throws BuildpackException {
        //dig into metadata for supported platforms.
        ObjectMapper om = new ObjectMapper();
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            JsonNode root = om.readTree(this.metadataJson);

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
