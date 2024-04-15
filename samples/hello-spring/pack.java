///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS dev.snowdrop:buildpack-client:0.0.6
import java.io.File;
import dev.snowdrop.buildpack.*;
import dev.snowdrop.buildpack.docker.*;

public class pack {

    public static void main(String... args) {
        int exitCode = BuildConfig.builder()
                           .withBuilderImage(new ImageReference("paketocommunity/builder-ubi-base"))        
                           .withOutputImage(new ImageReference("snowdrop/hello-spring:latest"))
                           .addNewFileContentApplication(new File("."))
                           .build()
                           .getExitCode();
    }
}
