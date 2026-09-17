# Karbone 0.2.0

First public release. Karbone is a Kotlin SDK for the [Carbone.io](https://carbone.io/) document generation API, cloud and on-premise.

## Highlights

- Templates: upload (legacy and versioned), download, update, delete, list with pagination, categories, tags.
- Rendering: one-call render with `download=true`, two-step render, asynchronous render with webhook, pure conversion, and the
  hash-first flow that uploads a local template only when Carbone does not know it yet.
- Typed `RenderOptions` and `OutputFormat` (PDF, image and CSV options), `RenderData` accepting kotlinx values, `JsonElement` or
  pre-serialized JSON.
- Errors as values through Arrow `Raise<KarboneError>`, with the raw Carbone message and error code preserved; `KarboneBlocking`
  facade for Java and non-coroutine code.
- `FakeKarbone` in-memory test double behind the `KarboneClient` interface.
- Zero HTTP dependency: JDK `HttpClient`, kotlinx.serialization, arrow-core. Compiled against the coroutines and serialization
  versions managed by Spring Boot 4.x.

## Changed since 0.1.4 (internal builds)

- Wire layer decodes responses through typed internal DTOs; an unparsable 2xx body yields `KarboneError.Serialization`.
- Snapshot builds are named from `localVersion` on CI.

## Documentation

https://ekino.github.io/Karbone/ — guides plus the generated API reference.
