# Releases

This reference defines Cardo's supported publication surface and immutable
release identity. A release is one SemVer value and one full Git revision across
Java artifacts, OpenAPI bundles, service images, the Git tag, and the release
manifest.

## Supported Artifacts

Consumers import `io.github.lutzseverino.cardo:cardo-bom:${cardo.version}` and
choose only the libraries they need. The BOM manages exactly these artifacts:

- `common-api`
- `common`
- `openapi-support`
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

The root `cardo` POM is published only because the module POMs inherit required
Maven metadata. It is not a consumer BOM. Identity, Invite, and Billing
executable JARs are not published to Maven Central. Neither the internal
`openapi-support:tests` classifier nor generated client transport packages are
part of the supported Java compatibility baseline.

A product imports the BOM and omits versions from individual Cardo dependencies:

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

Every library release includes its POM, binary JAR, source JAR, Javadoc JAR,
CycloneDX dependency inventory, signatures, and Central checksums. Binary JAR
manifests identify `Implementation-Version` and the full `Build-Revision`.
Every staged POM, including `cardo` and `cardo-bom`, records that same full
revision in its SCM tag.

Service images use only these public names:

- `ghcr.io/lutzseverino/cardo/identity:${version}`
- `ghcr.io/lutzseverino/cardo/invite:${version}`
- `ghcr.io/lutzseverino/cardo/billing:${version}`

Exact-version tags are aliases. Deployments record and pull the immutable
`name@sha256:...` reference from `release-manifest.json`. Cardo does not publish
`latest`, major/minor floating tags, or multi-architecture manifests in this
release slice.

## Versioning And Compatibility

Cardo uses SemVer, including standard prerelease identifiers. The checkout
default remains `0.1.0-SNAPSHOT`; release automation injects `revision` without
rewriting every POM. Tags have the form `v${version}`.

The previous compatibility baseline is the most recent non-draft,
non-prerelease release and is selected through its manifest. japicmp compares
all 13 supported JARs. Released OpenAPI files are checked with the same bundled
contract comparison used by pull-request CI. The first stable release records
that it has no prior stable baseline, while CI still exercises additive and
breaking fixtures for both gates.

An intentional Java break requires one squash commit containing all of:

- `!` in its Conventional Commit subject;
- a `BREAKING CHANGE:` footer with impact and migration;
- `Java-Migration: #N` naming the tracked migration.

OpenAPI breaks use the same title and footer requirements plus
`OpenAPI-Migration: #N`. Authorization makes the incompatibility reviewable; it
does not suppress the report.

Ordinary removal of a supported API requires deprecation in at least one
released minor first. Release notes must curate the replacement and migration;
generated diffs and commit lists are supporting evidence, not release notes.

## Release Evidence

`release-manifest.json` records the version, source revision, previous stable
baseline, 13 Maven coordinates and SHA-256 values, OpenAPI asset checksums, and
the exact image names, digests, and SBOM checksums. GitHub release assets also
contain the Central bundle, OpenAPI bundles, CycloneDX image inventories,
vulnerability report, and a SHA-256 checksum list. GitHub artifact attestations
cover the release files and the three registry digests.

The candidate and publication jobs independently build every service image.
Each image build starts from a clean runner by installing the exact release
version and revision once for the `identity,invite,billing` reactor with its
upstream modules. It then invokes the direct image goal separately for each
executable service, so a resumed second dispatch does not depend on ambient or
published Cardo artifacts and never applies the image goal to library modules.
Before any registry write, publication requires all three local image content
IDs and normalized CycloneDX inventories to match the validated candidate byte
for byte.

The workflow stages Central with `USER_MANAGED`; automatic publication is
disabled. It never overwrites a Maven version or an image tag. A rerun can
resume only when Central bytes and any existing registry digests equal the
recorded release. The draft manifest must identify the requested version and
revision, its digest must identify the preserved signed bundle, and that
bundle's unsigned payload must equal a fresh candidate even if `main` has since
advanced. Mixed or different state fails and requires a new version.
Central state classification checks every staged non-checksum payload file,
including sources, Javadocs, dependency inventories, and signatures; only an
all-404 result is safe to upload and only an all-identical result can resume.
The signed Central bundle is preserved in a draft release before the first
Publisher API call. If the call succeeds but recording its deployment ID does
not, a rerun stops for portal inspection instead of creating another
`USER_MANAGED` deployment.

## Dependency And Vulnerability Ownership

Dependabot checks Maven and Actions dependencies weekly. Pull requests run
dependency review and reject newly introduced high-severity dependencies.
Release preparation queries repository Dependabot alerts and records a
vulnerability report. Open high or critical findings block release unless
`release/vulnerability-exceptions.json` contains an owned, time-bounded
exception.

New critical findings are triaged within one business day and high findings
within three business days. An exception must name the alert, owner, reason,
tracking issue, and expiry; expiry is never longer than 30 days without a new
review. The exception file rejects unrecognized root or entry fields so a typo
cannot silently weaken the release gate. Medium and low findings are triaged in
the next planned maintenance cycle. Disabling an alert without this record is
not an exception process.

## Ownership Boundary

Cardo owns artifact bytes, public coordinates, compatibility checks, image
construction, manifests, and publication validation. Deployment repositories
own environment configuration, database migration sequencing, rollout,
rollback of environment state, and selection of an already published digest.
Publishing a Cardo release never deploys it.
