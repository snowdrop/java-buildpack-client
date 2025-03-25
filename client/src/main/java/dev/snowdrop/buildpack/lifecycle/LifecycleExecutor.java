package dev.snowdrop.buildpack.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import dev.snowdrop.buildpack.BuildConfig;
import dev.snowdrop.buildpack.BuilderImage;
import dev.snowdrop.buildpack.config.ImageReference;
import dev.snowdrop.buildpack.docker.ContainerUtils;
import dev.snowdrop.buildpack.docker.ImageUtils;
import dev.snowdrop.buildpack.docker.ImageUtils.ImageInfo;
import dev.snowdrop.buildpack.lifecycle.phases.Analyzer;
import dev.snowdrop.buildpack.lifecycle.phases.Detector;
import dev.snowdrop.buildpack.lifecycle.phases.Restorer;

public class LifecycleExecutor {

    private static final Logger log = LoggerFactory.getLogger(LifecycleExecutor.class);

    private final BuildConfig config;
    private final LifecyclePhaseFactory factory;
    private final Version activePlatformLevel;
    private final boolean useCreator;

    private boolean useCreator(boolean extensionsPresent, Boolean trustBuilder) {
        if(trustBuilder==null){
            log.debug("Trusted Builder not requested, extensions are present? "+extensionsPresent);
            return !extensionsPresent;
        }

        if(!trustBuilder){
            log.debug("Trusted builder explicitly set to false");
            return false;
        }else{
            if(extensionsPresent) {
                log.info("request to trust builder ignored, as extensions are present");
                return false;
            }
            log.debug("request to trust builder honored, will use creator");
            return true;
        }
    }  

    public LifecycleExecutor(BuildConfig config, BuilderImage originalBuilder, BuilderImage extendedBuilder, String activePlatformLevel) {
        this.config = config;
        this.useCreator = useCreator(extendedBuilder.hasExtensions(), config.getPlatformConfig().getTrustBuilder());
        this.activePlatformLevel = new Version(activePlatformLevel);
        this.factory = new LifecyclePhaseFactory(config.getDockerConfig(),
                                                 config.getBuildCacheConfig(),
                                                 config.getLaunchCacheConfig(),
                                                 config.getKanikoCacheConfig(),
                                                 config.getPlatformConfig(),
                                                 config.getLogConfig(),
                                                 config.getOutputImage(),
                                                 originalBuilder,
                                                 extendedBuilder,
                                                 activePlatformLevel);
    }

    public int execute() {
        int rc;
        try{
            //have factory create volumes for caches/application etc
            factory.createVolumes(config.getApplication());

            //do build phases, pay attention to useCreator & activePlatformLevel
            if(useCreator) {
                //although creator phase doesn't support extensions, it's possible
                //the build may select an order/group that doesn't make use of 
                //any extensions that are present, so we should not prevent use of
                //creator if the builder image has extensions.

                //create and run the creator phase
                rc = runPhase(factory.getCreator());
            } else {
                do{
                    Analyzer analyzer = (Analyzer)factory.getAnalyzer();
                    Detector detector = (Detector)factory.getDetector();

                    //spec below 0.7 use detect/analyze ordering, 0.7 and above use analyze/detect ordering
                    if(activePlatformLevel.lessThan("0.7")) {
                        rc=runPhase(detector);
                        if(rc!=0) break;
            
                        rc=runPhase(analyzer);
                        if(rc!=0) break;                        
                    }else{
                        rc=runPhase(analyzer);
                        if(rc!=0) break;

                        rc=runPhase(detector);
                        if(rc!=0) break;
                    }

                    //after detector.. a new run image may have been set in the analyzed.toml
                    //read this and correct it if the image name is badly formed.
                    //image names without a tag will cause docker-java to download EVERY tag, this is a huge amount of 
                    //data and will lead to timeout before completion.
                    if(activePlatformLevel.atLeast("0.10") && factory.getBuilderImage().hasExtensions()){

                        //read analyzed.toml, and update the run reference to a sanitized form.
                        ImageReference runRef = updateRunReference(detector);

                        if(runRef!=null){
                            //pull the new image in case restorer is going to read from it.
                            //in platforms 0.12 and above, restorer in daemon mode will read run image metadata from the 
                            //image stored in the daemon, rather than from a registry.
                            if(config.getDockerConfig().getUseDaemon()){
                                pullRunImage(runRef);
                            }

                            //update the run image for our builder image with the sanitized run image.
                            factory.getBuilderImage().setRunImage(runRef);
                        }  
                    }

                    Restorer restorer = (Restorer)factory.getRestorer();
                    rc=runPhase(restorer);
                    if(rc!=0) break;

                    //restorer can update the image reference, this has been observed during multi-arch daemon builds,
                    //test if the analyzed.toml run ref is still the one we 
                    if(config.getDockerConfig().getUseDaemon()){
                        //in daemon mode, at platforms 0.10 and 0.12, the image may now be specified by digest
                        //if the run image is multi-arch, restorer updates the analyzed.toml to add the digest of the architecture specific image
                        //this will not always match the digest for the run image pulled after detect with architecture, depending on container runtime
                        //repull the run image if it has a digest, just to be certain, else exporter will fail when the image is not present.
                        ImageReference runRef = getRunReference(restorer);

                        //if the new runRef has a digest, and no longer matches the expected run image, then update the builder's runImage.
                        if(runRef!=null && runRef.digestPresent() && !factory.getBuilderImage().getRunImages(activePlatformLevel)[0].equals(runRef)){
                            pullRunImage(runRef);
                            //update the run image for our builder image with the digest specified run image.
                            factory.getBuilderImage().setRunImage(runRef);
                        }
                    }

                    boolean extendedRunImage=false;
                    if(activePlatformLevel.atLeast("0.10") && factory.getBuilderImage().hasExtensions()){
                        //if run image extension is requested, we need to drive the run image extension phase, alongside build image extension.
                        extendedRunImage = isRunImageExtensionRequired(restorer,activePlatformLevel);
                    }

                    if(activePlatformLevel.atLeast("0.10") && factory.getBuilderImage().hasExtensions()){
                        rc=runPhase(factory.getBuildImageExtender());
                        if(rc!=0) break;

                        //if platform is atleast 0.12, and analyzerToml run-image.extend is true, we must run run image extender.
                        if(extendedRunImage){
                            rc=runPhase(factory.getRunImageExtender());
                            if(rc!=0) break;
                        }
                    }else{
                        rc=runPhase(factory.getBuilder());
                        if(rc!=0) break;
                    }
            
                    //if platform is at least 0.12, and run image extension happened, add -extended flag to exporter
                    rc=runPhase(factory.getExporter(extendedRunImage));
                    if(rc!=0) break;
                }while(false);
            }
            if(rc==0){
                log.info("Buildpack build success.");
                log.info("Buildpack build phases complete, application image is at "+config.getOutputImage().getReferenceWithLatest());
            }
            return rc;            
        }finally{
            //allow factory to clean up any volumes created
            factory.tidyUp();
        }
    }

    private ImageReference updateRunReference(LifecyclePhaseAnalyzedTomlUpdater phase){
        ImageReference runRef = getRunReference(phase);
        if(runRef!=null){
            String oldToml = new String(phase.getAnalyzedToml());
            String newToml = oldToml.replaceAll(runRef.getReference(),runRef.getReferenceWithLatest());     
            phase.updateAnalyzedToml(newToml);
            return runRef;
        }else{
            return null;
        }
    }

    private ImageReference getRunReference(LifecyclePhaseAnalyzedTomlUpdater phase){
        byte[] analyzedToml = phase.getAnalyzedToml();
        if(analyzedToml == null){
            return null;
        }
        TomlParseResult analyzed = Toml.parse(new String(analyzedToml));
        String newRunImage = analyzed.getString("run-image.reference");
        return new ImageReference(newRunImage);
    }

    private boolean isRunImageExtensionRequired(LifecyclePhaseAnalyzedTomlUpdater phase, Version activePlatformLevel){
        boolean extendedRunImage = false;
        if(activePlatformLevel.atLeast("0.12")){
            byte[] analyzedToml = phase.getAnalyzedToml();
            TomlParseResult analyzed = Toml.parse(new String(analyzedToml));

            Boolean extend = analyzed.getBoolean("run-image.extend");
            extendedRunImage = extend == null ? false : extend.booleanValue();
        }
        return extendedRunImage;
    }

    private void pullRunImage(ImageReference runRef) {
        //pull the new image.. (use platform read from builder image)
        log.debug("Pulling new Run Image by sha"); 
        ImageUtils.pullImages(config.getDockerConfig(), factory.getBuilderImage().getImagePlatform(), runRef);

        //collect the run image id/digests for debug (helpful in edge case run image mismatches)
        ImageInfo ii = ImageUtils.inspectImage(config.getDockerConfig().getDockerClient(), runRef);
        log.debug("new Run Image ID "+ii.id+" with Digests "+ii.digest+" Tags "+ii.tags+" for platform "+ii.platform);
    }

    private int runPhase(LifecyclePhase phase){
        ContainerStatus phaseRC = phase.runPhase(config.getLogConfig().getLogger(), config.getLogConfig().getUseTimestamps());
        ContainerUtils.removeContainer(config.getDockerConfig().getDockerClient(), phaseRC.getContainerId());
        return phaseRC.getRc(); 
    }
}
