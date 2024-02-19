package dev.snowdrop.buildpack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.snowdrop.buildpack.config.DockerConfig;
import dev.snowdrop.buildpack.config.ImageReference;
import dev.snowdrop.buildpack.config.PlatformConfig;
import dev.snowdrop.buildpack.docker.ImageUtils;
import dev.snowdrop.buildpack.docker.ImageUtils.ImageInfo;
import dev.snowdrop.buildpack.lifecycle.Version;
import dev.snowdrop.buildpack.utils.BuildpackMetadata;

public class BuilderImage {

    private int DEFAULT_USER_ID = 1000;
    private int DEFAULT_GROUP_ID = 1000;

    private ImageReference image;

    private int userId;
    private int groupId;

    private boolean hasExtensions;

    private String metadataJson;
    private ImageReference runImage;
    private List<String> runImages;

    private List<String> builderSupportedPlatforms;

    public BuilderImage(BuilderImage original, boolean addedExtensions, ImageReference extended) {
        this.userId = original.userId;
        this.groupId = original.groupId;
        this.runImages = original.runImages;
        this.builderSupportedPlatforms = original.builderSupportedPlatforms;
        this.hasExtensions = original.hasExtensions || addedExtensions;
        System.out.println("Extended builder image hasExtensions? "+hasExtensions+" orig?"+original.hasExtensions+" add?"+addedExtensions);
        this.image = extended;
    }

    public BuilderImage(DockerConfig dc, PlatformConfig pc, ImageReference runImage, ImageReference builderImage){
        image = builderImage;

        // pull and inspect the builderImage to obtain builder metadata.
        ImageUtils.pullImages(dc.getDockerClient(), 
                              dc.getPullTimeout(), 
                              builderImage.getReference());

        ImageInfo ii = ImageUtils.inspectImage(dc.getDockerClient(), 
                                               builderImage.getReference());

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
        System.out.println("Builder image got xtnLayers label "+xtnLayers);
        hasExtensions = (xtnLayers!=null && !xtnLayers.isEmpty());
        System.out.println("BuilderImage hasExtensions? "+hasExtensions);
        
        //defer the calculation of run images to the getter, because we 
        //need to know the selected platform level to know how to find the run metadata.
        metadataJson = ii.labels.get("io.buildpacks.builder.metadata"); 
        this.runImage = runImage; 
        runImages = null;

        builderSupportedPlatforms = BuildpackMetadata.getSupportedPlatformsFromMetadata(metadataJson);
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
    public List<String> getRunImages(Version activePlatformLevel) {
        if(runImages==null){
            runImages = new ArrayList<>();
            if(runImage!=null){
                // use user specified run image
                runImages.add(runImage.getReference());
            }else{
                Set<String> runSet = new HashSet<>();
                if(activePlatformLevel.atLeast("0.12")){
                    runSet.addAll(BuildpackMetadata.getRunImageFromRunTOML(image.getReference()));
                }else{
                    // obtain the buildpack metadata json identified runImage
                    runSet.add(BuildpackMetadata.getRunImageFromMetadataJSON(metadataJson));
                }
                runImages.addAll(runSet);
            }
        }
        return runImages;
    }
    public List<String> getBuilderSupportedPlatforms() {
        return builderSupportedPlatforms;
    }
    
}
