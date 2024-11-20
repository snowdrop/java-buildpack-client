# Buildpack Builder config example

Example of a java project able to build a Quarkus, spring Boot, ... project
using the DSL `BuildConfig.builder()`

To use it, just configure the following env var pointing to a project to be built as a container image

```bash
exort PROJECT_PATH=<JAVA_PROJECT>
```
and execute this command in a terminal:
```bash
mvn compile exec:java
```