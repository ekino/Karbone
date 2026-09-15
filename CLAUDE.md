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

## Testing

Kotest 6 on JUnit Platform plus the `io.kotest` Gradle plugin (`libs.bundles.kotest.extended`: runner, assertions, property, kotlin-test; data-driven `withData` is built into the engine) plus MockK.
Write specs as `ShouldSpec` classes named `*Spec` under `src/test/kotlin/com/ekino/oss/karbon`, e.g.:

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
