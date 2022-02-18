# Java buildpack Client

![Build](https://github.com/snowdrop/java-buildpack-client/actions/workflows/build.yml/badge.svg)
[![Maven Central](https://img.shields.io/maven-central/v/dev.snowdrop/buildpack-client.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22dev.snowdrop%22%20AND%20a:%22buildpack-client%22)

Prototype of a simple buildpack (https://buildpacks.io/) client for java.

This project represents a simple implementation of the buildpack platform spec, 
and can be used to build projects using specified buildpacks. 

The client uses the combined `creator` lifecycle phase from the buildpack to 
drive the entire build. A fluent interface is provided to allow creation & configuration
of the build to be performed. 

A very simple build can be performed with as little as.
```
Buildpack.builder()
    .withContent(new File("/home/user/java-project"))
    .withFinalImage("test/testimage:latest")
    .build();
```

This will use the default builder image from (https://packeto.io) to handle the build
of the project in the `/home/user/java-project` folder. The resulting image will 
be stored in the local docker daemon as `test/testimage:latest`.

## Overview

The [`BuildpackBuilder`](src/main/java/dev/snowdrop/buildpack/BuildpackBuilder.java) offers other configuration methods to customise behavior. 

- run/build Image can be specified
- docker socket location can be configured
- cache volume names for build/launch can be specified, and optionally auto deleted after the build
- `creator` debug level can be set
- pull timeout can be configured in seconds

Options exist, but are not (yet) active, for.

- use docker registry instead of daemon (requires additional auth config, not implemented yet)
- passing Env content to the build stage (not implemented yet)

A variety of methods are supported for adding content to be build, content is combined in the order
passed, allowing for sparse source directories, or multiple project dirs to be combined. 

- File/Directory, with prefix. Eg, take this directory /home/fish/wibble, and make it appear in the application content as /prodcode
- String Content, with path. Eg, take this String content "FISH" and make it appear in the application content as /prodcode/fish.txt
- InputStream Content, with path. Similar to String, except with data pulled from an InputStream.
- [`ContainerEntry`](src/main/java/dev/snowdrop/buildpack/docker/ContainerEntry.java) interface, for custom integration.


Build/RunImages will be pulled as required. 

The builder will use docker via the `DOCKER_HOST` env var, if configured, or via the platform appropriate docker socket if not.
Alternatively, dockerHost can be directly configured on the builder itself. If the docker host starts with `unix://` the path to the
docker socket is extracted and used during the build phase. If unset, this defaults to `/var/run/docker/sock`

## How To:

Want to try out this project? The packages/api are not fixed in stone yet, so be aware! But here are the basic steps to get you up and running. 


1. Add this project as a dependency via jitpack. 
```
        <dependency>
            <groupId>dev.snowdrop</groupId>
            <artifactId>buildpack-client</artifactId>
            <version>0.0.4</version>
        </dependency> 
```

2. Instantiate a BuildpackBuilder
```
    BuildpackBuilder bpb = Buildpack.builder();
```

3. Define the content to be built..
```
    bpb = bpb.addNewFileContent(new File("/path/to/the/project/to/build));
```

4. Configure the name/tags for the image to create
```
    bpb = bpb.withFinalImage("myorg/myimage:mytag");
```

5. Invoke the build
```
    bpb.build();
```

Or combine all the above steps into a single callchain. 
```
Buildpack.builder()
    .withFileContent(new File("/path/to/the/project/to/build))
    .withFinalImage("myorg/myimage:mytag")
    .build();
```

There are many more ways to customize & configure the BuildpackBuilder, take a look at the [interface](src/main/java/dev/snowdrop/buildpack/BuildpackBuilder.java) to see everything thats currently possible. 

A demo project has been created to play with the Java `BuildpackBuilder` [here](https://github.com/snowdrop/java-buildpack-demo) :-)

Most likely if you are using this to integrate to existing tooling, you will want to supply a custom LogReader to receive the messages output by BuildPacks during the build. You may also want to associate cache names to a project, to enable faster rebuilds for a given project. 

## Logging

Output from the Buildpack execution is available via the `dev.snowdrop.buildpack.Logger` interface, which can be optionally be passed using the builder.
At the moment two kinds of logger are supported:

- SystemLogger (default)
- Slf4jLogger

Both can be configured using the builder:

```java
      Buildpack.builder()
        .withContent(new File("."))
        .withFinalImage("test/my-image:latest")
        .withLogger(new SystemLogger())
        .build();

```

or 

```java
      Buildpack.builder()
        .withContent(new File("."))
        .withFinalImage("test/my-image:latest")
        .withLogger(new Slf4jLogger())
        .build();
```


### Inline Logger configuration

The builder DSL supports inlining `Logger` configuration:

```java

      Buildpack.builder()
        .withContent(new File("."))
        .withFinalImage("test/my-image:latest")
        .withNewSystemLogger(false) //Explicitly disables ansi colors
        .build();

```

The above statement configures system logger with disabled ansi colors.

Similarly, with `Slf4jLogger` one can inline the name of the logger:

```java

      Buildpack.builder()
        .withContent(new File("."))
        .withFinalImage("test/my-image:latest")
        .withNewSlf4jLogger(MyApp.class.getCanonicalName()) //Explicitly specify the Logger
        .build();

```



## Using the buildpack client with jbang

The easiest way to invoke arbitrary java code, without much hassle is by using [jbang](https://www.jbang.dev/).

So, you can drop the following file in your project:

```java
///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS dev.snowdrop:buildpack-client:0.0.4
import static java.lang.System.*;

import java.io.File;
import dev.snowdrop.buildpack.*;

public class pack {

    public static void main(String... args) {
      Buildpack.builder()
        .withContent(new File("."))
        .withFinalImage("test/my-image:latest")
        .build();
    }
}

```

... and just run it using:

```
./pack.java
```

See how it's used in the included [samples](./samples).

## FAQ:

**Will this work with Podman?:**

Not yet. Once the regular `pack` cli works with Podman, I'll revisit this and ensure it works too. 

**Does this work on Windows?:**

Yes.. it's supposed to! 
Tested with Win10 + Docker on WSL2

**Does this work on Linux?:**

Yes.. with Docker (real docker, not podman pretending to be docker). 
Tested with Ubuntu + Docker



