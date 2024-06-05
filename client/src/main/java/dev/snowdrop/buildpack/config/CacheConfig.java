package dev.snowdrop.buildpack.config;

import io.sundr.builder.annotations.Buildable;

@Buildable(generateBuilderPackage=true, builderPackage="dev.snowdrop.buildpack.builder")
public class CacheConfig {

    private static Boolean DEFAULT_DELETE_CACHE = Boolean.TRUE;

    public static CacheConfigBuilder builder() {
        return new CacheConfigBuilder();
    }

    private String cacheVolumeName;
    private Boolean deleteCacheAfterBuild;

    public CacheConfig(String cacheVolumeName,
                       Boolean deleteCacheAfterBuild){
        this.cacheVolumeName = cacheVolumeName;
        this.deleteCacheAfterBuild = deleteCacheAfterBuild!=null ? deleteCacheAfterBuild : DEFAULT_DELETE_CACHE; //default if not set
    }

    public String getCacheVolumeName(){
        return this.cacheVolumeName;
    }

    public Boolean getDeleteCacheAfterBuild(){
        return this.deleteCacheAfterBuild;
    }
}
