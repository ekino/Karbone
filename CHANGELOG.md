# Changelog

All notable changes to Karbone are documented here. Detailed notes for each release live in `release-notes/`.

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
