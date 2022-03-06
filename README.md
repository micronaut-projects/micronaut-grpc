# Micronaut GRPC

[![Maven Central](https://img.shields.io/maven-central/v/io.micronaut.grpc/micronaut-grpc-runtime.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.micronaut.grpc%22%20AND%20a:%22micronaut-grpc-runtime%22)
[![Build Status](https://github.com/micronaut-projects/micronaut-grpc/workflows/Java%20CI/badge.svg)](https://github.com/micronaut-projects/micronaut-grpc/actions)
[![Revved up by Gradle Enterprise](https://img.shields.io/badge/Revved%20up%20by-Gradle%20Enterprise-06A0CE?logo=Gradle&labelColor=02303A)](https://ge.micronaut.io/scans)

This project includes integration between [Micronaut](http://micronaut.io) and [GRPC](https://grpc.io).

## Documentation

See the [Documentation](https://micronaut-projects.github.io/micronaut-grpc/latest/guide) for more information.

See the [Snapshot Documentation](https://micronaut-projects.github.io/micronaut-grpc/snapshot/guide) for the current development docs.

## Examples

Examples for Java, Kotlin and Groovy can be found in the [examples](https://github.com/micronaut-projects/micronaut-grpc/tree/master/examples) directory.

## Snapshots and Releases

Snaphots are automatically published to [JFrog OSS](https://oss.jfrog.org/artifactory/oss-snapshot-local/) using [Github Actions](https://github.com/micronaut-projects/micronaut-grpc/actions).

See the documentation in the [Micronaut Docs](https://docs.micronaut.io/latest/guide/index.html#usingsnapshots) for how to configure your build to use snapshots.

Releases are published to JCenter and Maven Central via [Github Actions](https://github.com/micronaut-projects/micronaut-grpc/actions).

A release is performed with the following steps:

* [Edit the version](https://github.com/micronaut-projects/micronaut-grpc/edit/master/gradle.properties) specified by `projectVersion` in `gradle.properties` to a semantic, unreleased version. Example `1.0.0`
* [Create a new release](https://github.com/micronaut-projects/micronaut-grpc/releases/new). The Git Tag should start with `v`. For example `v1.0.0`.
* [Monitor the Workflow](https://github.com/micronaut-projects/micronaut-grpc/actions?query=workflow%3ARelease) to check it passed successfully.
* Celebrate!
