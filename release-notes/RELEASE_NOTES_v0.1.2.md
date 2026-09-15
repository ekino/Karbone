# Karbone 0.1.2

Internal review build.

## Changed

- The blocking facade now works over the `KarboneClient` interface: `KarboneClient.blocking()` (Kotlin extension) and
  `KarboneBlocking.of(client)` (Java) accept the real `Karbone` or `FakeKarbone`. `Karbone.blocking()` is unchanged.
  Consumers no longer need to re-implement `runBlocking { either { } }` → `KarboneException` to test blocking code.
