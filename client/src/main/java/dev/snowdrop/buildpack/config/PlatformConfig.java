package dev.snowdrop.buildpack.config;

import java.util.HashMap;
import java.util.Map;

import io.sundr.builder.annotations.Buildable;

@Buildable(generateBuilderPackage=true, builderPackage="dev.snowdrop.buildpack.builder")
public class PlatformConfig {

    public static PlatformConfigBuilder builder() {
        return new PlatformConfigBuilder();
    }

    private String DEFAULT_PLATFORM_LEVEL="0.10";

    private String platformLevel;
    private Map<String,String> environment;
    private ImageReference lifecycleImage;
    private Boolean trustBuilder; //use creator when possible.
    
    public PlatformConfig( 
                   String platformLevel,
                   ImageReference lifecycleImage,
                   Map<String, String> environment,
                   Boolean trustBuilder){
        this.platformLevel = platformLevel!=null ? platformLevel : DEFAULT_PLATFORM_LEVEL;
        this.environment = environment!=null ? environment : new HashMap<>();
        this.lifecycleImage = lifecycleImage;
        this.trustBuilder = trustBuilder;
    }

    public String getPlatformLevel(){
        return platformLevel;
    }

    public Map<String,String> getEnvironment(){
        return environment;
    }

    public ImageReference getLifecycleImage(){
        return lifecycleImage;
    }

    public Boolean getTrustBuilder(){
        return trustBuilder;
    }


}
