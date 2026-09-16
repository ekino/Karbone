# Changelog

All notable changes to Karbone are documented here. Detailed notes for each release live in `release-notes/`.

## 0.1.4 — 2026-09-16

- Generated API reference (Dokka) published under `/api/` of the documentation site; KDoc on every public declaration.
- `User-Agent` reports the real SDK version, read from the jar manifest (`dev` outside a jar).
- Documentation readability pass: option tables in the rendering guide, links to the API reference instead of inline enumerations.
- Docs header shows the project name and the latest release version next to the brand mark.

## 0.1.3 — 2026-09-16

- `Karbone.baseUrl` and `Karbone.apiVersion` read-only accessors (log-safe), replacing the removed `config` property.
- Compiled against kotlinx-coroutines 1.10.2 (Spring Boot 4.x managed version) to avoid `NoSuchMethodError` on `runBlocking` when the BOM downgrades the runtime.
- Kotlin 2.4.20, Gradle 9.7.1.

## 0.1.2 — 2026-09-15

- Blocking facade over `KarboneClient`: `KarboneClient.blocking()` and `KarboneBlocking.of(client)`, so `FakeKarbone` fits behind it.
- `Karbone.config` removed from the public API.

## 0.1.1 — 2026-09-15

- Retried transport failures are logged at `WARN`.
- Logging guide, isolated and anonymized development notes, "Trying a build" page.
- GitLab CI publishing to the Package Registry (tags and manual snapshots); optional signing; public-release guards.

## 0.1.0 — 2026-09-15

First internal review build: Carbone templates (upload, download, update, delete, list), rendering (sync, two-step, async webhook,
convert, hash-first from bytes), typed options and errors with Arrow `Raise`, blocking facade for Java, `FakeKarbone` test double,
Quarkdown documentation.
