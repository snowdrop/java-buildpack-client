# java-buildpack-client
Prototype of a simple buildpack (https://buildpacks.io/) client for java.. 

This project represents a simple implementation of the buildpack platform spec, 
and can be used to build projects using specified buildpacks. 

The client uses the combined `creator` lifecycle phase from from the buildpack to 
drive the entire build. A fluent interface is provided to allow creation & configuration
of the build to be performed. 

A very simple build can be performed with as little as.. 
```
		BuildPackBuilder.get()
			.withContent("",new File("/home/user/java-project"))
			.withFinalImage("test/testimage:latest")
			.build();
```

This will use the default builder image from (https://packeto.io) to handle the build
of the project in the `/home/user/java-project` folder. The resulting image will 
be stored in the local docker daemon as `test/testimage:latest`

## Overview

The [`BuildPackBuilder`](src/com/redhat/buildpack/BuildPackBuilder.java) offers other configuration methods to customise behavior. 

- run/build Image can be specified
- docker socket location can be configured
- cache volume names for build/launch can be specified, and optionally auto deleted after the build
- `creator` debug level can be set
- pull timeout can be configured in seconds

Options exist, but are not (yet) active, for.. 

- use docker registry instead of daemon (requires additional auth config, not implemented yet)
- passing Env content to the build stage (not implemented yet)

A variety of methods are supported for adding content to be build, content is combined in the order
passed, allowing for sparse source directories, or multiple project dirs to be combined. 

- File/Directory, with prefix. Eg, take this directory /home/fish/wibble, and make it appear in the application content as /prodcode
- String Content, with path. Eg, take this String content "FISH" and make it appear in the application content as /prodcode/fish.txt
- InputStream Content, with path. Similar to String, except with data pulled from an InputStream.
- [`ContainerEntry`](src/com/redhat/buildpack/docker/ContainerEntry.java) interface, for custom integration.

Output from the Builpack execution is available via the `BuildPackBuilder.LogReader` interface, which can be optionally be passed 
to the `build` invocation. The `build` signature that doesn't accept a `LogReader` supplies it's own default reader that will pass
messages to stdout/stderr.

Build/RunImages will be pulled as required. 

The builder will use docker via the `DOCKER_HOST` env var, if configured, or via the platform appropriate docker socket if not.
Alternatively, dockerHost can be directly configured on the builder itself. If the docker host starts with `unix://` the path to the
docker socket is extracted and used during the build phase. If unset, this defaults to `/var/run/docker/sock`


## FAQ:

**Will this work with Podman?:**

Not yet. Once the regular `pack` cli works with Podman, I'll revisit this and ensure it works too. 

**Does this work on Windows?:**

Yes.. it's supposed to! 
Tested with Win10 + Docker on WSL2

**Does this work on Linux?:**

Yes.. with Docker (real docker, not podman pretending to be docker). 
Tested with Ubuntu + Docker



