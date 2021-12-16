///usr/bin/env jbang "$0" "$@" ; exit $?
// //DEPS <dependency1> <dependency2>

//DEPS dev.snowdrop:buildpack-client:0.0.3-SNAPSHOT
import static java.lang.System.*;

import java.io.File;
import dev.snowdrop.buildpack.*;

public class pack {

    public static void main(String... args) {
      BuildpackBuilder.get()
        .withContent(new File("."))
        .withFinalImage("snowdrop/hello-spring:latest")
        .build(new LogRelay());
    }
}
