# Karbone 0.1.2

Internal review build.

## Changed

- The blocking facade now works over the `KarboneClient` interface: `KarboneClient.blocking()` (Kotlin extension) and
  `KarboneBlocking.of(client)` (Java) accept the real `Karbone` or `FakeKarbone`. `Karbone.blocking()` is unchanged.
  Consumers no longer need to re-implement `runBlocking { either { } }` → `KarboneException` to test blocking code.
- `Karbone.config` is no longer exposed: the configuration is a constructor detail, not part of the public API.

## Internal

- `FakeKarbone.list` filters lazily; `listAll` no longer captures the `Raise` context.
- Test cleanups: `either { }` without explicit type arguments, stub server closes the exchange with `use`.
