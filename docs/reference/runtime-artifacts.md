# Runtime Artifacts

This reference is authoritative for the executable service artifacts and the
process contract Cardo supplies to deployment repositories. It does not define
an environment topology, credentials, or artifact publication policy.

## Artifacts

The Maven reactor produces one executable Spring Boot JAR for each service:

| Service | JAR | Default image name | Default port |
| --- | --- | --- | --- |
| Identity | `identity/target/identity-${version}.jar` | `cardo/identity:${version}` | `8081` |
| Invite | `invite/target/invite-${version}.jar` | `cardo/invite:${version}` | `8083` |
| Billing | `billing/target/billing-${version}.jar` | `cardo/billing:${version}` | `8085` |

Build all executable JARs with the canonical reactor command:

```bash
./mvnw --batch-mode --no-transfer-progress verify
```

Images use Cloud Native Buildpacks rather than repository-specific Dockerfiles.
The builder and run image are pinned by multi-architecture digest in the root
POM. From a clean checkout, install the reactor artifacts locally and build the
desired service image:

```bash
./mvnw --batch-mode --no-transfer-progress -DskipTests install
./mvnw --batch-mode --no-transfer-progress -pl identity package spring-boot:build-image-no-fork
./mvnw --batch-mode --no-transfer-progress -pl invite package spring-boot:build-image-no-fork
./mvnw --batch-mode --no-transfer-progress -pl billing package spring-boot:build-image-no-fork
```

Override `cardo.image.name` when a deployment or release build needs a different
local name. Releases use only the private
`ghcr.io/lutzseverino/cardo/{identity,invite,billing}:${version}` packages.
Authorized deployment repositories pin and pull the digest recorded by the
[release manifest](releases.md). Registry publication belongs to the release
workflow, not to these builds.

## Runtime Contract

JARs run with `java -jar`. Images retain the buildpack launcher and its declared
non-root user; deployments must not replace that user with root. Both artifact
forms accept Spring Boot externalized configuration at process start, including
environment variables and mounted configuration files. Cardo-owned safe and
local-development defaults remain in `application.yml`; deployment-specific
values, credentials, and topology are never baked into the image. Service-specific
inputs remain documented by the service READMEs, and deployment repositories own
their values and secrets. The formal fail-fast production configuration policy is
owned by issue #32 rather than this packaging contract.

The only exposed Actuator endpoints are `health` and `info`. These unauthenticated
paths support orchestration and artifact inspection:

| Path | Meaning |
| --- | --- |
| `/actuator/health/liveness` | The process is live. Restart it when this is not healthy. |
| `/actuator/health/readiness` | The process and its database can accept traffic. Route traffic only while the overall status and `components.db.status` are `UP`. |
| `/actuator/info` | Build version and full source revision metadata. |

Application routes keep their existing security contract. Other Actuator
endpoints are not exposed.

The services use Spring Boot graceful shutdown. On `SIGTERM`, readiness is
withdrawn and the embedded server stops accepting new work while active requests
finish. The web-server shutdown phase has a 20-second bound. An orchestrator
should send `SIGTERM`, stop routing immediately, and allow at least 25 seconds
before forcing termination to account for observation and process-exit overhead.
Readiness also fails when the service database is unavailable; liveness remains isolated so a
database outage withdraws traffic without creating a restart loop.
The readiness group exposes component names and statuses so orchestrators can read
`components.db.status`; health details and other health groups retain their existing visibility.

## Image Metadata

Every image carries the following OCI labels:

- `org.opencontainers.image.title`
- `org.opencontainers.image.description`
- `org.opencontainers.image.version`
- `org.opencontainers.image.revision`
- `org.opencontainers.image.source`
- `org.opencontainers.image.licenses`

The revision label and `/actuator/info` source revision are the full Git commit
used by Maven. The version is the Maven project version. No deployment endpoint,
credential, topology, or secret is included in this metadata.

Maven archives and OCI creation timestamps use a fixed source epoch. The smoke
rebuilds every service JAR and rebuilds one representative image with fresh
caches, requiring byte-identical JAR hashes and an identical image content ID.
This catches timestamps or mutable build inputs that would make the same commit
produce different artifacts.

## Repository Smoke Validation

Run the complete portable-artifact smoke from a clean checkout with a
Docker-compatible daemon:

```bash
./scripts/smoke-runtime-artifacts.sh
```

The smoke installs the reactor locally, starts an isolated disposable PostgreSQL
for each artifact, packages and starts every JAR, builds and starts every image,
and verifies reproducibility, metadata, non-root image ownership, exact public
probe paths, database-backed readiness, readiness withdrawal, bounded termination, and
cleanup. Identity uses a local protocol stub containing the exact clients,
canonical claim mappers, fixed Identity roles, and provider read capabilities
required by its startup contract. The stub returns `403` for mapper and role
definition writes and `404` for unexpected requests, proving that artifact
startup does not rely on provider mutation. It is not a Keycloak emulator; the
Identity integration suite separately exercises the contract against a
digest-pinned disposable Keycloak. The unpublished
`integration/reference-stack` module supplies the full product integration
environment and starts only checkout-built executable JARs. Its smoke first
installs one fresh `identity,invite,billing,integration/reference-stack` reactor
with upstream modules, preserving the same fresh-reactor invariant used by
release image construction.
