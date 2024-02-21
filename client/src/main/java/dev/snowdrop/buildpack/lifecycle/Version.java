package dev.snowdrop.buildpack.lifecycle;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.snowdrop.buildpack.BuildpackException;

public class Version {
    String version;
    int major;
    int minor;

    final Pattern p = Pattern.compile("^(\\d+)\\.(\\d+)(\\..*)?$");

    public Version(String v){
        version = v;
        Matcher m = p.matcher(v);
        if(m.find()){
            major = Integer.parseInt(m.group(1));
            minor = Integer.parseInt(m.group(2));
        }else{
            throw new BuildpackException("Invalid format for Version "+v, new IllegalArgumentException());
        }
    }

    public String toString() {
        return version;
    }

    public boolean equals(Version v){
        return this.major == v.major && this.minor == v.minor;
    }
    public boolean greaterThan(Version v){
        return this.major > v.major || ( this.major == v.major && this.minor > v.minor);
    }
    public boolean atLeast(Version v){
        return this.major > v.major || ( this.major == v.major && this.minor >= v.minor);
    }
    public boolean atMost(Version v){
        return this.major < v.major || ( this.major == v.major && this.minor <= v.minor);
    }
    public boolean lessThan(Version v){
        return this.major < v.major || ( this.major == v.major && this.minor < v.minor);
    }

    //convenience methods to keep code a little more readable.
    public boolean equals(String ver){
        return this.equals(new Version(ver));
    }
    public boolean greaterThan(String ver){
        return this.greaterThan(new Version(ver));
    }
    public boolean atLeast(String ver){
        return this.atLeast(new Version(ver));
    }
    public boolean atMost(String ver){
        return this.atMost(new Version(ver));
    }
    public boolean lessThan(String ver){
        return this.lessThan(new Version(ver));
    }
}
