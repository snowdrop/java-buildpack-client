package dev.snowdrop.buildpack.config;

public class ImageReference {
    private String refString;
    public ImageReference(String reference){
        this.refString = reference;
    }

    public String getReference(){
        return this.refString;
    }
}
