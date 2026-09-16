# Module Karbone

Kotlin SDK for the [Carbone.io](https://carbone.io/) document generation API. Start from `Karbone` (`cloud` / `onPremise`), then
`Templates` and `Renders`. Every I/O method is `context(_: Raise<KarboneError>) suspend fun`; Java callers use `KarboneBlocking`.
The guides live on the [documentation site](../index.html).

# Package com.ekino.oss.karbone

Entry point (`Karbone`, `KarboneClient`), configuration, the `Templates` / `Renders` interfaces and the `KarboneError` hierarchy.

# Package com.ekino.oss.karbone.model

Request and response models: template sources and identifiers, render data and options, output formats with their typed options,
rendered documents.

# Package com.ekino.oss.karbone.blocking

Synchronous, exception-based facade for Java and non-coroutine code.

# Package com.ekino.oss.karbone.testing

`FakeKarbone`, the in-memory `KarboneClient` for consumer tests.
