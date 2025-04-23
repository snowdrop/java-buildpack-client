# Java buildpack Client

![Build](https://github.com/snowdrop/java-buildpack-client/actions/workflows/build.yml/badge.svg)
[![Maven Central](https://img.shields.io/maven-central/v/dev.snowdrop/buildpack-client.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22dev.snowdrop%22%20AND%20a:%22buildpack-client%22)

Prototype of a simple buildpack (https://buildpacks.io/) client for java.

This project represents a simple implementation of the buildpack platform spec, 
and can be used to build projects using specified buildpacks. 

This project implements up to version 0.10 of the buildpack platform spec, and should
work with builders requesting from 0.4 through to 0.10. 0.12 is available but experimental.

A fluent interface is provided to allow creation & configuration
of the build to be performed. 

A very simple build can be performed with as little as.
```java
int exitCode = BuildConfig.builder()
                           .withOutputImage(new ImageReference("test/testimage:latest"))
                           .addNewFileContentApplication(new File("/home/user/java-project"))
                           .build()
                           .getExitCode();
```

This will use the default builder image from (https://paketo.io) to handle the build
of the project in the `/home/user/java-project` folder. The resulting image will 
be stored in the local docker daemon as `test/testimage:latest`.

## Overview

The [`BuildpackConfig`](client/src/main/java/dev/snowdrop/buildpack/BuildConfig.java) offers other configuration methods to customise behavior. 

- run/build/output Image can be specified
- docker can be configured with.. 
    - pull timeout 
    - pull retry count (will retry image pull on failure)
    - pull retry timeout increase (increases timeout each time pull is retried)
    - host
    - network
    - docker socket path
    - if Daemon should be used or not. (If yes, docker socket is mounted into build container so buildpack can make use of daemon directly, 
                                        if no, then docker will read/create output images with remote registry directly. Note that daemon is 
                                        still used to run the various build containers, just that the containers do not themselves have access to the daemon.)
    - per registry authentication information. (see registry authentication section below)
- caches (launch/build/kaniko) can be configured with.. 
    - cache volume name. (if omitted, a randomly generated name is used)
    - cache delete after build. (if yes, cache volume will be removed after build exits, defaults to TRUE)
- logging from the build containers can be customized..
    - log level, can be info, warn, debug. Affects the amount of verbosity from the lifecycle during build. 
    - logger instance, system logger (to sysout/syserr) and slf4j loggers are supplied.
- platform aspects can be customized.. 
    - platform level can be forced to a version, by default platform level is derived from the intersection of builder/lifecycle and platform supported versions. 
    - environment vars can be set that will be accessible during the build as platform env vars.
    - the builder can be 'trusted'. This means the creator lifecycle is used, where all phases happen within a single container, faster, but does not support extensions, and can expose some lifecycle phases to credentials that may be otherwise protected. 
    - lifecycle image can be specified. If set, the lifecycle from the specified image will be used instead of the one within the builder image. Allows for easy testing with newer lifecycles. 

A variety of methods are supported for adding content to be build, content is combined in the order
passed, allowing for sparse source directories, or multiple project dirs to be combined. 

- File/Directory, with prefix. Eg, take this directory /home/fish/wibble, and make it appear in the application content as /prodcode
- String Content, with path. Eg, take this String content "FISH" and make it appear in the application content as /prodcode/fish.txt
- InputStream Content, with path. Similar to String, except with data pulled from an InputStream.
- [`ContainerEntry`](client/src/main/java/dev/snowdrop/buildpack/docker/ContainerEntry.java) interface, for custom integration.

Build/RunImages will be pulled as required. 

The builder will use docker via the `DOCKER_HOST` env var, if configured. If `DOCKER_HOST` is not set, the builder will check if `podman` is on the path, and if so, will use it to configure the `DOCKER_HOST` value, and to locate the correct path to use if mounting the socket into the containers (`.withUseDaemon(true)` mode, the default). If `podman` is not on the path, docker is assumed, and `unix:///var/run/docker.sock` is used as a default.

## How To:

Want to try out this project? The packages/api are not fixed in stone yet, so be aware! But here are the basic steps to get you up and running. 


1. Add this project as a dependency.. (use the latest version instead of XXX)
```xml
        <dependency>
            <groupId>dev.snowdrop</groupId>
            <artifactId>buildpack-client</artifactId>
            <version>0.0.14</version>
        </dependency> 
```

2. Instantiate a BuildConfig
```java
    BuildConfig bc = BuildConfig.builder();
```

3. Define the content to be built..
```java
    bc = bc.addNewFileContentApplication(new File("/path/to/the/project/to/build));
```

4. Configure the name/tags for the image to create
```java
    bc = bc.withOutputImage(new ImageReference("myorg/myimage:mytag"));
```

5. Invoke the build
```java
    bc.build();
```

6. Retrieve the build exit code
```java
    bc.getExitCode();
```

Or combine all the above steps into a single callchain. 
```java
int exitCode = BuildConfig.builder()
                           .withOutputImage(new ImageReference("test/testimage:latest"))
                           .addNewFileContentApplication(new File("/home/user/java-project"))
                           .build()
                           .getExitCode();
```

There are many more ways to customize & configure the BuildConfig, take a look at the [interface](client/src/main/java/dev/snowdrop/buildpack/BuildConfig.java) to see everything thats currently possible. 

A demo project has been created to allow easy exploration of uses of `BuildConfig` [here](https://github.com/snowdrop/java-buildpack-demo) :-)

Most likely if you are using this to integrate to existing tooling, you will want to supply a custom LogReader to receive the messages output by the Build Containers during the build. You may also want to associate cache names to a project, to enable faster rebuilds for a given project. Note that if you wish caches to survive beyond a build, you should set `deleteCacheAfterBuild` to `false` for each cache. Eg. 

```java
int exitCode = BuildConfig.builder()
                          .withNewBuildCacheConfig()
                              .withCacheVolumeName("my-project-specific-cache-name")
                              .withDeleteCacheAfterBuild(true)
                              .and()
                          .withOutputImage(new ImageReference("test/testimage:latest"))
                          .addNewFileContentApplication(new File("/home/user/java-project"))
                          .build()
                          .getExitCode();
```

## Logging

Output from the Buildpack execution is available via the `dev.snowdrop.buildpack.Logger` interface, which can be optionally be passed using the builder.
At the moment two kinds of logger are supported:

- SystemLogger (default)
- Slf4jLogger

Both can be configured using the builder:

```java
int exitCode = BuildConfig.builder()
                          .withNewLogConfig()
                              .withLogger(new SystemLogger())
                              .withLogLevel("debug")
                              .and()
                          .withOutputImage(new ImageReference("test/testimage:latest"))
                          .addNewFileContentApplication(new File("/home/user/java-project"))
                          .build()
                          .getExitCode();

```

or 

```java
int exitCode = BuildConfig.builder()
                          .withNewLogConfig()
                              .withLogger(new Slf4jLogger())
                              .withLogLevel("debug")
                              .and()
                          .withOutputImage(new ImageReference("test/testimage:latest"))
                          .addNewFileContentApplication(new File("/home/user/java-project"))
                          .build()
                          .getExitCode();
```


### Inline Logger configuration

The builder DSL supports inlining `Logger` configuration:

```java

int exitCode = BuildConfig.builder()
                          .withNewLogConfig()
                              .withNewSystemLogger(false)
                              .withLogLevel("debug")
                              .and()
                          .withOutputImage(new ImageReference("test/testimage:latest"))
                          .addNewFileContentApplication(new File("/home/user/java-project"))
                          .build()
                          .getExitCode();

```

The above statement configures system logger with disabled ansi colors.

Similarly, with `Slf4jLogger` one can inline the name of the logger:

```java

int exitCode = BuildConfig.builder()
                          .withNewLogConfig()
                             .withNewSlf4jLogger(MyApp.class.getCanonicalName())
                             .withLogLevel("debug")
                             .and()
                          .withOutputImage(new ImageReference("test/testimage:latest"))
                          .addNewFileContentApplication(new File("/home/user/java-project"))
                          .build()
                          .getExitCode();

```

## Error Handling

If the build fails for any reason, a `BuildpackException` will be thrown, this is a RuntimeException, so does not need an explicit catch block. There are many ways in which a build can fail, from something environmental, like docker being unavailable, to build related issues, like the chosen builder image requiring a platformlevel not implemented by this library. 

## Registry Authentication

The client supports per registry authentication, which can be configured by supplying RegistryAuthConfig instances to the the DockerConfig. 

Eg. 

```java

    List<RegistryAuthConfig> authInfo = new ArrayList<>();

    RegistryAuthConfig authConfig = RegistryAuthConfig.builder()
                                            .withRegistryAddress("quay.io")
                                            .withUsername("username")
                                            .withPassword("password")
                                            .build();

    authInfo.add(authConfig);

```

and then setting the list of auth configs into the docker config on the builder using

```java

exitCode = BuildConfig.builder()
                           .withBuilderImage(new ImageReference(builderImage))
                           .withOutputImage(new ImageReference("quay.io/myexamplerepo/myexampleimage:exampletag"))
                           .withNewDockerConfig()
                              .withAuthConfigs(authInfo)
                              .withUseDaemon(false)
                           .and()
                           .addNewFileContentApplication(new File("/home/user/java-project"))
                           .build()
                           .getExitCode();
```

The client will use this information when talking to the local daemon to pull images, and will use it to build a CNB_REGISTRY_AUTH variable to enable the lifecycle to talk to registries too. This is most useful when using the `.withUseDaemon(false)` option, as the lifecycle will need to be able to pull and push layers to the appropriate registries. 

When encoding CNB_REGISTRY_AUTH, the client supports username/password, auth (pre-encoded username/password value), and registrytoken (passed as bearer token header). If your registry requires a more complex header, setting CNB_REGISTRY_AUTH into the platform environment will disable the client generation of the value from the passed authconfig objects. 

Note: if you pass CNB_REGISTRY_AUTH via platform env _and_ pass authconfigs, it is up to the caller to ensure the values result in the required behavior. 

- CNB_REGISTRY_AUTH is used by the lifecycle phases running in the containers to talk to registries. 
- RegistryAuthConfigs are used by the client to talk to the daemon, and pull images from registries. 


## Invoking the buildpack client 

### Jbang

The easiest way to invoke arbitrary java code, without much hassle is by using [jbang](https://www.jbang.dev/).

So, you can drop the following file in your project: (swap XXX for latest release of buildpack-client)

```java
///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS dev.snowdrop:buildpack-client:0.0.14}
import static java.lang.System.*;

import java.io.File;
import dev.snowdrop.buildpack.*;

public class pack {

    public static void main(String... args) {
        int exitCode = BuildConfig.builder()
                                .withOutputImage(new ImageReference("test/testimage:latest"))
                                .addNewFileContentApplication(new File("/home/user/java-project"))
                                .build()
                                .getExitCode();
        System.exit(exitCode);
    }
}

```

... and just run it using:

```
./pack.java
```

The samples use jbang too, but allow the version of the library to be set via an env var for use in our tests! [samples](./samples).

### Maven exec:java

To play with the Java Buildpack client & DSL, use the following simple java project: [samples/build-me](samples/build-me)

To use it, configure the following mandatory environment variables pointing to a project (example: [hello-quarkus](samples/hello-quarkus), [hello-spring](samples/hello-spring)) to be built as a container image

```bash
export PROJECT_PATH=<JAVA_PROJECT>
# <IMAGE_REF> can be a local reference with a name and tag without registry, or a full registry reference with host, port(optional), path & tag
export IMAGE_REF=<IMAGE_REF> 
```

If you plan to push your image to a registry, then set your registry credential using these variables:
```bash
export REGISTRY_USERNAME="<REGISTRY_USERNAME>"
export REGISTRY_PASSWORD="<REGISTRY_PASSWORD>"
export REGISTRY_SERVER="docker.io"
```

Execute this command in a terminal:
```bash
mvn compile exec:java
```
You can also pass the `BP_` or `CNB_` environment variables:
```bash
export BP_JVM_VERSION="21"
export CNB_LOG_LEVEL=debug
etc
```

## FAQ:

**Will this work with podman?**

Yes, tested against podman 4 and 5, in rootless and rootful modes, on macos and linux. 

Note you probably will need to update the podman socket timeout, to avoid a disagreement between the docker-java api and podman.. 
_(as per https://github.com/testcontainers/testcontainers-java/issues/7310 )_

- Linux:
```bash
          sudo mkdir -p /etc/containers/containers.conf.d
          printf "[engine]\nservice_timeout=91\n" | sudo tee -a /etc/containers/containers.conf.d/service-timeout.conf
```

- Mac:
```bash
          echo 'mkdir -p /etc/containers/containers.conf.d && printf "[engine]\nservice_timeout=91\n" > /etc/containers/containers.conf.d/service-timeout.conf && systemctl restart podman.socket' |  podman machine ssh --username root --
```


**Does this work on Windows?:**

Yes.. it's supposed to! (although the automated test suite on github is unable to run as the runners don't support nested virtualization)
Tested with Win10 + Docker on WSL2

**Does this work on Linux?:**

Yes.. Developed and tested on Fedora, and tested via Github runners on Ubuntu with podman 4/5, and docker.

**Can I supply buildpacks/extensions to add to a builder like pack?:**

The code is structured to allow for this, but the feature is not exposed via the builder api,
as using additional buildpacks/extensions requires updating order.toml in the base builder,
and that would require additional config to either override, or specify the behavior for manipulating
the toml. If you are interested in this kind of functionality, raise an Issue so I can better understand
the use case, or send a PR!



