package dev.snowdrop.buildpack.lifecycle;

import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import dev.snowdrop.buildpack.BuildConfig;
import dev.snowdrop.buildpack.BuilderImage;
import dev.snowdrop.buildpack.docker.ContainerUtils;
import dev.snowdrop.buildpack.docker.ImageUtils;
import dev.snowdrop.buildpack.lifecycle.phases.Analyzer;
import dev.snowdrop.buildpack.lifecycle.phases.Detector;

public class LifecycleExecutor {

    private final BuildConfig config;
    private final LifecyclePhaseFactory factory;
    private final Version activePlatformLevel;
    private final boolean useCreator;

    private boolean useCreator(boolean extensionsPresent, Boolean trustBuilder) {
        if(trustBuilder==null){
            return extensionsPresent;
        }

        if(!trustBuilder) 
            return false;
        else{
            if(extensionsPresent) {
                config.getLogConfig().getLogger().stdout("request to trust builder ignored, as extensions are present");
                return false;
            }

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

                    //move this to new method
                    boolean extendedRunImage=false;
                    if(activePlatformLevel.atLeast("0.10") && factory.getBuilderImage().hasExtensions()){
                        byte[] analyzerToml = detector.getAnalyzedToml();
                        
                        TomlParseResult analyzed = Toml.parse(new String(analyzerToml));
                        String newRunImage = analyzed.getString("run-image.reference");

                        if(activePlatformLevel.atLeast("0.12")){
                            Boolean extend = analyzed.getBoolean("run-image.extend");
                            extendedRunImage = extend == null ? false : extend.booleanValue();
                        }

                        //pull the new image.. 
                        ImageUtils.pullImages(config.getDockerConfig().getDockerClient(), factory.getDockerConfig().getPullTimeout(), newRunImage);

                        //update run image associated with our builder image.
                        factory.getBuilderImage().getRunImages(activePlatformLevel).clear();
                        factory.getBuilderImage().getRunImages(activePlatformLevel).add(newRunImage);

                        //TODO: if config.getRunImage is non-null, should we override the run-image from an extension here? 
                    }

                    rc=runPhase(factory.getRestorer());
                    if(rc!=0) break;

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
            return rc;            
        }finally{
            //allow factory to clean up any volumes created
            factory.tidyUp();
        }
    }


    private int runPhase(LifecyclePhase phase){
        ContainerStatus phaseRC = phase.runPhase(config.getLogConfig().getLogger(), config.getLogConfig().getUseTimestamps());
        ContainerUtils.removeContainer(config.getDockerConfig().getDockerClient(), phaseRC.getContainerId());
        return phaseRC.getRc(); 
    }
}
