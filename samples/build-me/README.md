# Buildpack Builder config example

Example of a java project able to build a Quarkus, Spring Boot, ... project
using the DSL `BuildConfig.builder()`

To use it, configure the following mandatory environment variables pointing to a project (example: [hello-quarkus](../hello-quarkus), [hello-spring](../hello-spring)) to be built as a container image

```bash
export PROJECT_PATH=<JAVA_PROJECT>
export IMAGE_REF=<IMAGE_REF> // <IMAGE_NAME> without registry or <REGISTRY_SERVER>/<REGISTRY_ORG>/<IMAGE_NAME>
```

**Important**: To avoid the `docker rate limit` problem, then set this `CNB_` environment variable to let `lifecycle` to get rid of the limit:
```bash
export CNB_REGISTRY_AUTH="'{"index.docker.io":"Basic <BASE64_OF_USERNAME:PASSWORD>"}'"

// Replace `<BASE64_OF_USERNAME:PASSWORD> text with
echo -n "username:password" | base64
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