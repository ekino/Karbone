<p align="center"><img src="assets/logo/karbone-mark.svg" alt="Karbone" width="120" height="120"></p>

# Karbone

A modern Kotlin SDK for the [Carbone.io](https://carbone.io/) document generation API.

Karbone aims to replace the official [carbone-sdk-java](https://github.com/carboneio/carbone-sdk-java), which is incomplete and no longer keeps up with the Carbone API, with an idiomatic, fully typed Kotlin client.

> Status: early development. The API is not stable yet.

## Installation

```kotlin
dependencies {
    implementation("com.ekino.oss:karbone:<version>")
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

Guides and the generated API reference are published with the documentation site (`docs/`, Quarkdown + Dokka).
Locally: `./gradlew dokkaGenerate` writes the API reference to `build/dokka/html`.

## Development

Requires JDK 21.

```bash
./gradlew spotlessApply   # format and add license headers
./gradlew build           # compile, detekt, spotlessCheck, tests
./gradlew kotest          # run tests with the Kotest reporter
```

## License

[MIT](LICENSE.md) © 2026 ekino
