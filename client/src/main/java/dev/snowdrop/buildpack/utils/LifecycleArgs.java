package dev.snowdrop.buildpack.utils;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LifecycleArgs {

    private static final Logger log = LoggerFactory.getLogger(LifecycleArgs.class);

    private final String lifecycle;
    private final List<String> args;
    private final String imageName;

    public LifecycleArgs(String lifecycle, String[] args, String finalImageName) {

        this.args = new ArrayList<>();

        if(System.getenv("DEBUG_LIFECYCLE")!=null || System.getProperty("DEBUG_LIFECYCLE")!=null) {
            log.info("Lifecycle debug detected, invoking wrapper...");
            this.lifecycle = "/cnb/lifecycle/debug";
            this.args.add(lifecycle);
        }else{
            this.lifecycle = lifecycle;
        }

        for(String arg: args){
            this.args.add(arg);
        }
        this.imageName = finalImageName;
    }

    public LifecycleArgs(String lifecycle, String finalImageName) {

        this.args = new ArrayList<>();

        if(System.getenv("DEBUG_LIFECYCLE")!=null || System.getProperty("DEBUG_LIFECYCLE")!=null) {
            log.info("Lifecycle debug detected, invoking wrapper...");
            this.lifecycle = "/cnb/lifecycle/debug";
            this.args.add(lifecycle);
        }else{
            this.lifecycle = lifecycle;
        }

        this.imageName = finalImageName;
    }

    public void addArg(String optName, String value){
        this.args.add(optName);
        this.args.add(value);
    }

    public void addArg(String optName){
        this.args.add(optName);
    }


    public List<String> toList(){
        ArrayList<String> allArgs = new ArrayList<>();
        allArgs.add(lifecycle);
        allArgs.addAll(args);
        if(imageName!=null)
            allArgs.add(imageName);
        return allArgs;        
    }

    public String[] toArray(){
        return toList().toArray(new String[]{});
    }

    public String toString(){
        return toList().toString();
    }




}
