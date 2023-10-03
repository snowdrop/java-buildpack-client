///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS dev.snowdrop:buildpack-client:0.0.8
import java.io.File;
import dev.snowdrop.buildpack.*;
import dev.snowdrop.buildpack.docker.*;

public class pack {

    public static void main(String... args) {
      Buildpack.builder()
        .addNewFileContent(new File("."))
        .withBuildImage("redhat/buildpacks-builder-quarkus-jvm:latest")
        .withFinalImage("snowdrop/hello-quarkus:latest")
        .build();
    }
}
