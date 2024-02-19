package dev.snowdrop.buildpack.utils;

import java.util.ArrayList;
import java.util.List;

public class LifecycleArgs {

    private final String lifecycle;
    private final List<String> args;
    private final String imageName;

    public LifecycleArgs(String lifecycle, String[] args, String finalImageName) {
        this.lifecycle = lifecycle;
        
        this.args = new ArrayList<>();
        for(String arg: args){
            this.args.add(arg);
        }
        this.imageName = finalImageName;
    }

    public LifecycleArgs(String lifecycle, String finalImageName) {
        this.lifecycle = lifecycle;
        this.args = new ArrayList<>();
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
