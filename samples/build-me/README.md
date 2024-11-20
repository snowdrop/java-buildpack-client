# Buildpack Builder config example

Example of a java project able to build a Quarkus, spring Boot, ... project
using the DSL `BuildConfig.builder()`

To use it, just configure the following env var pointing to a project to be built as a container image

```bash
export PROJECT_PATH=<JAVA_PROJECT>
export IMAGE_REF=<IMAGE_REF> // quay.io/<ORG>/<IMAGE_NAME> or <IMAGE_NAME>
```
and execute this command in a terminal:
```bash
mvn compile exec:java
```