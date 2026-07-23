# Releases

This reference defines Cardo's public product-integration surface, private
runtime surface, and immutable release identity. One release is one SemVer and
one full Git revision across Central artifacts, private images, the Git tag,
and the release manifest.

## Public Maven Surface

Products import `io.github.lutzseverino.cardo:cardo-bom:${cardo.version}` and
choose only the libraries they need. The BOM manages exactly:

- `common-api`
- `common`
- `cardo-openapi-contracts`
- `authorization-keycloak-client`
- `authorization-security`
- `authorization`
- `identity-client`
- `identity-client-http`
- `identity-product-auth`
- `invite-client`
- `invite-client-http`
- `billing-client`
- `billing-client-http`

Authorization remains public deliberately because products embed its mechanics
in product-owned transactions. Products still own resource catalogs, actions,
tenant meaning, permissions, and domain policy. Public artifacts contain no
credentials, deployment topology, environment state, or product rules.

`cardo-openapi-contracts` contains these byte-for-byte resources:

- `META-INF/cardo/openapi/common/openapi/errors.yaml`
- `META-INF/cardo/openapi/identity/openapi/identity.yaml`
- `META-INF/cardo/openapi/invite/openapi/invite.yaml`
- `META-INF/cardo/openapi/billing/openapi/billing.yaml`

Their relative layout preserves shared-error references after extraction. The
same JAR bytes and document checksums are the durable OpenAPI compatibility
baseline. Consumers resolve or extract this artifact from Central; release
assets and a Cardo checkout are not required.

The root `cardo` reactor/build parent, `openapi-support` and its tests
classifier, the executable Identity/Invite/Billing JARs, and
`integration-reference-stack` are explicitly denied from Central. Published
POMs are flattened, self-contained, and do not resolve the private root parent.

Every public JAR has its POM, binary JAR, source JAR, Javadoc JAR, CycloneDX
inventory, signatures, and Central checksums. Binary manifests carry
`Implementation-Version` and the full `Build-Revision`; every staged POM carries
the same revision in its SCM tag.

Example product consumption requires neither a Cardo checkout nor private
credentials:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.github.lutzseverino.cardo</groupId>
      <artifactId>cardo-bom</artifactId>
      <version>${cardo.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
<dependencies>
  <dependency>
    <groupId>io.github.lutzseverino.cardo</groupId>
    <artifactId>identity-client</artifactId>
  </dependency>
</dependencies>
```

## Private Runtime Surface

The exact private image tags are:

- `ghcr.io/lutzseverino/cardo/identity:${version}`
- `ghcr.io/lutzseverino/cardo/invite:${version}`
- `ghcr.io/lutzseverino/cardo/billing:${version}`

Images are private from their first push. Existing packages must already report
REST visibility `private`; a new package must report `private` immediately after
its first push. The packages remain unlinked from every repository, and the
public source repository is not granted Actions access. Cardo never invokes a
visibility-changing or package-access API. `latest`, floating major/minor tags,
and multi-platform manifests are outside this slice.

Deployment repositories receive package `Read` access and pull the immutable
`name@sha256:...` reference from an approved manifest with their own scoped
credentials. Cardo publication uses the protected `GHCR_PUBLISH_TOKEN`, never
the workflow `GITHUB_TOKEN`, for package API reads and pushes. Before package
metadata is trusted, the token's current-user response must identify the
intended `lutzseverino` owner. Its protected post-publication verification uses
a separate `GHCR_PULL_TOKEN` limited to `read:packages`; its automatic
`GITHUB_TOKEN` has only `contents: read`.
Deployment repositories own credentials, configuration, rollout, rollback, and
environment evidence; Cardo publication never deploys.

## Versioning And Compatibility

Cardo uses SemVer including prereleases. The checkout remains
`0.1.0-SNAPSHOT`; release automation injects `revision` without rewriting every
POM. Tags are deterministic annotated `v${version}` tags created before any
GitHub draft can claim the name. An existing tag must be annotated and peel to
the exact requested revision before staging or resume mutates release state.
GitHub release classification comes from that same validated version: a
version with prerelease identifiers is always a prerelease, while a stable
version is a normal release. A resumed draft is corrected to the exact
version's classification before publication.

The previous compatibility baseline is the most recent non-draft,
non-prerelease release selected through its manifest. japicmp compares all
public Java library JARs. OpenAPI comparison downloads the previous immutable
`cardo-openapi-contracts` JAR from Central, verifies its artifact and document
hashes, extracts it, and compares it with the current contracts.

An intentional Java break needs `!`, a `BREAKING CHANGE:` footer with impact
and migration, and `Java-Migration: #N` in one squash commit. OpenAPI breaks use
the same title/footer requirements plus `OpenAPI-Migration: #N`. Ordinary
removal requires deprecation in at least one released minor first.

## Release Evidence And Resumability

`release-manifest.json` records the version, revision, prior stable baseline,
public Maven coordinates and hashes, the contract artifact and document hashes,
and private image identities. Central uses `USER_MANAGED`; automatic publication
is disabled. The signed bundle is preserved before its first upload.

The current public MIT repository means any runtime manifest, digest, SBOM, or
vulnerability evidence attached to its GitHub release is publicly disclosed.
It must contain no credentials, configuration, topology, or environment state.
Before future proprietary runtime work or repository-boundary changes, move
runtime evidence behind access control; public Central bytes and contracts stay
public permanently.

Candidate and publication jobs build each image independently from the exact
version and revision and require identical content IDs and normalized
inventories before a registry write. Before Central staging, every runtime
package must be absent or private and unlinked from every repository.
Publication records each service digest after its tag is pushed or verified,
REST-asserts private and unlinked state, and logs out. A subsequent fresh
read-only verification job requests each exact manifest digest without
credentials and requires an explicit `401 UNAUTHORIZED` response;
network, registry, malformed, or other HTTP failures do not prove denial. It
then uses the scoped pull token to pull each recorded digest. A failed run
keeps already-recorded digests in its draft manifest and a focused Actions
evidence artifact. A rerun requires a recorded image digest to match exactly.
If an abrupt runner loss left a private tag without a recorded digest, the
remote image must pull to the freshly rebuilt candidate's exact Docker content
ID before its registry digest is recovered.
Central bytes, private package visibility, version, and revision remain
immutable; mixed or different state requires a new version. Image digest
attestations are retained in GitHub's attestation store but are not pushed to
GHCR.

Any public runtime exposure is an incident and makes that version
non-resumable. Deleting and recreating an exposed package is containment and
namespace recovery only; it cannot erase anonymous availability or rehabilitate
the version. The workflow rejects both `0.1.0-rc.1` and `0.1.0-rc.2` before
checkout. The latter is an immutable public-Maven-only partial prerelease: it
has no private image identities or digest proofs and is non-deployable and
non-resumable. `0.1.0-rc.3` is the next candidate for the first complete
public-library/private-runtime prerelease required before the release objective
can close.

Dependabot and dependency review own dependency findings. Open high or critical
findings block release unless `release/vulnerability-exceptions.json` has an
owned, time-bounded exception. Critical findings are triaged within one business
day and high findings within three; an exception must name its alert, owner,
reason, tracking issue, and expiry.
