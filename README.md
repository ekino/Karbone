<p align="center"><img src="assets/logo/karbone-mark.svg" alt="Karbone" width="120" height="120"></p>

# Karbone

[![Build and Test](https://github.com/ekino/Karbone/actions/workflows/build.yml/badge.svg)](https://github.com/ekino/Karbone/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.ekino.oss/karbone?label=maven-central)](https://central.sonatype.com/artifact/com.ekino.oss/karbone)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE.md)
[![Java 21](https://img.shields.io/badge/Java-21-orange)](https://openjdk.java.net/projects/jdk/21/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-purple)](https://kotlinlang.org/)
[![Carbone API](https://img.shields.io/badge/Carbone%20API-v5-1c1b1a)](https://carbone.io/api-reference.html)
[![Documentation](https://img.shields.io/badge/docs-ekino.github.io%2FKarbone-0e0e0e)](https://ekino.github.io/Karbone/)

A modern Kotlin SDK for the [Carbone.io](https://carbone.io/) document generation API.

Karbone aims to replace the official [carbone-sdk-java](https://github.com/carboneio/carbone-sdk-java), which is incomplete and no longer keeps up with the Carbone API, with an idiomatic, fully typed Kotlin client.

> Status: early public release (0.x). The API may still change between minor versions.

## Installation

```kotlin
dependencies {
    implementation("com.ekino.oss:karbone:0.2.0")
}
```

Snapshots are published to the Maven Central snapshot repository:

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://central.sonatype.com/repository/maven-snapshots/") }
}
```

## Documentation

Guides and the generated API reference: **https://ekino.github.io/Karbone/**

- [Quickstart](https://ekino.github.io/Karbone/quickstart/) · [Configuration](https://ekino.github.io/Karbone/configuration/)
- [Rendering](https://ekino.github.io/Karbone/rendering/) · [Templates](https://ekino.github.io/Karbone/templates/) · [Errors](https://ekino.github.io/Karbone/errors/)
- [On-premise](https://ekino.github.io/Karbone/on-premise/) · [Testing](https://ekino.github.io/Karbone/testing/) · [Logging](https://ekino.github.io/Karbone/logging/)
- [API reference](https://ekino.github.io/Karbone/api/)

The site is built from `docs/` (Quarkdown) and `./gradlew dokkaGenerate` (Dokka, `build/dokka/html`).

## Development

Requires JDK 21.

```bash
./gradlew spotlessApply   # format and add license headers
./gradlew build           # compile, detekt, spotlessCheck, tests
./gradlew kotest          # run tests with the Kotest reporter
```

## License

[MIT](LICENSE.md) © 2026 ekino
