# Karbone 0.1.1

Internal review build.

## Changed

- A transport failure that is retried (with `RetryPolicy.OnTransportError`) is now logged at `WARN` instead of `DEBUG`.

## Documentation

- New **Logging** guide: backend detection, logger names, levels, what is never logged, configuration examples.
- Temporary **Development notes** section isolated under `docs/dev/`, anonymized, and aligned with the shipped code.
- **Trying a build** page: how to consume builds from the GitLab Package Registry during the review.

## Build and CI

- GitLab CI publishes to the project's Package Registry: automatically on `v*` tags, manually for snapshots on `main`.
- Artifacts are signed only when a signing key is provided.
- GitHub release and docs workflows refuse to run while `docs/dev/` exists.
