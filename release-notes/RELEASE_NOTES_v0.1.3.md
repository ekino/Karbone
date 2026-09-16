# Karbone 0.1.3

Internal review build.

## Added

- `Karbone.baseUrl` and `Karbone.apiVersion`: read-only, log-safe accessors replacing the `config` property removed in 0.1.2.
  The token and the rest of the configuration stay private.

## Fixed

- `NoSuchMethodError: kotlinx.coroutines.BuildersKt.runBlockingK$default` in the blocking facade when Spring Boot's BOM pins
  coroutines to 1.10.2: Karbone is now compiled against coroutines 1.10.2, the version managed by Spring Boot 4.x.

## Build

- Kotlin 2.4.20, Gradle 9.7.1.
