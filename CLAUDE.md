# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Karbon is a new Kotlin SDK for the [Carbone.io](https://carbone.io/) document-generation API, meant to replace the official
[carbone-sdk-java](https://github.com/carboneio/carbone-sdk-java), which is incomplete and dated. Published as `com.ekino.oss:karbon`
under the MIT license. Package root: `com.ekino.oss.karbon`.

The build setup is deliberately copied from the sibling project `../Metalastic` (same author, same conventions). When in doubt about
tooling, look there first.

## Toolchain

- Kotlin 2.4.10, JVM toolchain 21 (foojay resolver), Gradle 9.7.0 via wrapper. Always use `./gradlew`.
- Single Gradle module (root project). Dependency versions live in `gradle/libs.versions.toml`; never hardcode versions in
  `build.gradle.kts`.
- Version: `localVersion` in `gradle.properties` locally; in GitHub Actions derived from `v*` tags or `git describe`.

## Code quality (enforced by `check`)

- **Spotless**: ktfmt Google style (2-space indent) for `.kt` and `.gradle.kts`, prettier (`.prettierrc.json`, width 160) for
  Markdown/YAML. Every `.kt` file must start with the copyright header `Copyright (c) 2026 ekino (https://www.ekino.com/)`;
  Spotless inserts it, so run `spotlessApply` rather than typing it.
- **Detekt** (`detekt.yml`, built on default config): max line length 160, `detektMain` is wired into `check`.

## Architecture

- **Public API** (`com.ekino.oss.karbon`): `KarbonClient` interface (what consumers depend on), `Karbon` entry point (`cloud(...)`,
  `onPremise(...)`, `create(config)`), `Templates` and `Renders` interfaces, `KarbonConfig`, `KarbonError` (sealed values) and
  `KarbonException` (blocking facade only). Models live in `model/`. `testing/FakeKarbon` is the in-memory double shipped for consumer tests.
- **Error style**: every I/O method is `context(_: Raise<KarbonError>) suspend fun`. Never throw for API or transport failures; `raise`
  a `KarbonError` and keep the raw Carbone `error` text in `message`. Request invariants use `ensure { InvalidRequest(...) }`.
  `blocking/KarbonBlocking` wraps calls in `either { }` and throws `KarbonException` for Java callers.
- **Layers** (`internal/`): `Calls` (URL building, transport/IO error → `KarbonError.Transport`), `DefaultTemplates` / `DefaultRenders`
  (endpoint logic, hash-first render: try `POST /render/{sha256}`, on 404 upload then retry once), `http/` (`HttpTransport` = bytes
  in/out, `JdkHttpTransport` on `java.net.http` with multipart writer, auth, `carbone-version`, retry), `wire/` (`RenderBody` builds the
  JSON body with Carbone wire names such as `SelectPdfVersion`; `Responses` parses the `{success, data, error}` envelope, maps HTTP
  status → `KarbonError.Api`, extracts filenames from `Content-Disposition`).
- Public models stay idiomatic (`PdfVersion.PDF_A_3`, `Watermark`...); wire names exist only in `wire/`. `explicitApi()` is on.
- `RenderData.Raw` exists on purpose: consumers using Jackson (iperia-back) pass pre-serialized JSON.
- A malformed bearer makes carbone-ee answer HTTP 500 "Invalid JSON Web Token"; `Responses.apiError` maps that message to `Unauthorized`.
- `renders.convert` sends no `data` (v5 spec) and retries with `data: {}` on a 422 "Missing data" from v4-behaving servers
  (carbone-ee 5.8 on-premise does this).

## Testing

Kotest 6 on JUnit Platform plus the `io.kotest` Gradle plugin (`libs.bundles.kotest.extended`: runner, assertions, property, kotlin-test; data-driven `withData` is built into the engine) plus MockK.
Write specs as `ShouldSpec` classes named `*Spec` under `src/test/kotlin/com/ekino/oss/karbon`. Unit specs stub Carbone with the JDK
`com.sun.net.httpserver.HttpServer` (no WireMock). `integration/CarboneContainerSpec` runs against `carbone/carbone-ee:full-5.8.0-fonts`
through Testcontainers and is skipped automatically when Docker is unavailable; `integration/CarboneAuthContainerSpec` does the same with
`CARBONE_AUTHENTICATION=true` using the test key pair in `src/test/resources/auth/` (ES512 JWT, claims `iss=carbone-user`, `aud=carbone-ee`); the docx fixture is `src/test/resources/templates/invoice.docx`
(built by hand, contains `{d.number}`, `{d.customer.name}`, `:formatC`, `:convEnum`). Example:

```kotlin
class FooSpec : ShouldSpec({ should("do X") { foo() shouldBe bar } })
```

## Branch working context

`ClaudeContexts/` is gitignored working material — audits, specs, PDFs, examples, throwaway
analyses — never committed. At the start of any work on a branch, list
`ClaudeContexts/branches/<type>/<name>/` (branch `feat/facturx` → `ClaudeContexts/branches/feat/facturx/`)
and read what is relevant to the task. It holds:

- `DECISIONS.md` — decisions taken on this branch, with the option rejected and why. Append to it
  when a real decision is made; read it first when resuming a branch.
- `CONTEXT.yaml` — Jira tickets, milestone, MR and commit trace, managed by the skills.
- anything else dropped there by hand.

Absent directory = nothing to read, not an error. Do not commit these files, and do not
recreate durable, cross-branch lessons here — those go to memory.

## Publishing and CI

- Maven Central via the vanniktech `maven-publish` plugin (Central Portal, `publishToMavenCentral(automaticRelease = true)`, signed).
  Credentials come from `ORG_GRADLE_PROJECT_mavenCentral*` / `ORG_GRADLE_PROJECT_signingInMemoryKey*` secrets.
- `.github/workflows`: `build.yml` (PR + main), `publish.yml` (on `v*` tags: publish + GitHub Release, using
  `release-notes/RELEASE_NOTES_<tag>.md` when present), `manual-publish.yml` (workflow_dispatch snapshot), `deploy-docs.yml`.

## Documentation site (Quarkdown)

Docs live in `docs/` and are built with [Quarkdown](https://quarkdown.com/) 2.6.0 using its `docs` library, not Writerside/VitePress
like Metalastic. Layout: `_setup.qd` (theme, shared setup, auto-included), `_nav.qd` (left sidebar links), `main.qd` (home + entry
point), one `.qd` per page starting with `.docname {..}` then `.include {docs}`. A page becomes a subdocument only when linked from
`_nav.qd` or another page. `docs/public/` is copied verbatim to the output root (CNAME etc.). Output lands in `build/docs/Karbon`
(directory named after `.docname`), deployed to GitHub Pages by `deploy-docs.yml` on pushes to `main`. Quarkdown is not a Gradle task;
install it locally (`brew install quarkdown-labs/quarkdown/quarkdown`) and run:

```bash
quarkdown c docs/main.qd -o build/docs --clean     # build the site
quarkdown c docs/main.qd -p -w                     # live preview with reload
```

## Commands

```bash
./gradlew spotlessApply          # format + add license headers (run before committing)
./gradlew build                  # compile, detekt, spotlessCheck, tests
./gradlew test                   # all tests (JUnit Platform, used by `check`)
./gradlew kotest                 # all tests via the io.kotest plugin, with Kotest's own console reporter
./gradlew test --tests "com.ekino.oss.karbon.KarbonSpec"                # one spec class
./gradlew test --tests "com.ekino.oss.karbon.KarbonSpec" -Dkotest.filter.tests="should expose*"   # one test by name glob
./gradlew detektMain             # static analysis only
./gradlew publishToMavenLocal    # local publication (vanniktech maven-publish)
```
