# Buildpack Builder config example

Example of a java project able to build a Quarkus, Spring Boot, ... project
using the DSL `BuildConfig.builder()`

To use it, configure the following mandatory environment variables pointing to a project (example: [hello-quarkus](../hello-quarkus), [hello-spring](../hello-spring)) to be built as a container image

```bash
export PROJECT_PATH=<JAVA_PROJECT>
# <IMAGE_REF> can be <IMAGE_NAME> without registry or a full registry reference with host, port(optional), path & tag
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
export BP_MAVEN_BUILT_ARTIFACT="target/quarkus-app/lib/ target/quarkus-app/*.jar target/quarkus-app/app/ target/quarkus-app/quarkus"
export CNB_LOG_LEVEL=debug
etc
```