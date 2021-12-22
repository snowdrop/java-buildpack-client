///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS dev.snowdrop:buildpack-client:0.0.4
import java.io.File;
import dev.snowdrop.buildpack.*;
import dev.snowdrop.buildpack.docker.*;

public class pack {

    public static void main(String... args) {
      Buildpack.builder()
        .addNewFileContent(new File("."))
        .withFinalImage("snowdrop/hello-spring:latest")
        .build();
    }
}
