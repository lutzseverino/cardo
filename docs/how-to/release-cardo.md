# Release Cardo

Only repository maintainers should run the protected manual `Release` workflow.
The workflow accepts an exact SemVer and full commit SHA, and rejects anything
other than the current `origin/main` revision.

## Steps

### Configure One-Time Prerequisites

1. Verify `io.github.lutzseverino.cardo` in the Maven Central Publisher Portal.
2. Create the protected GitHub `release` environment with required reviewers.
3. Add `CENTRAL_TOKEN_USERNAME`, `CENTRAL_TOKEN_PASSWORD`, `GPG_PRIVATE_KEY`,
   and `GPG_PASSPHRASE` only to that environment.
4. Publish the signing public key through a durable public key service.
5. Protect `v*` tags and enable immutable GitHub releases.
6. Enable the dependency graph, vulnerability alerts, and Dependabot security
   updates.
7. Permit the workflow's job-scoped package, attestation, and contents writes.

Missing credentials, namespace ownership, package visibility, alert access, or
repository protection must fail the release. Do not weaken a gate to make the
first release pass.

### Prepare And Run

1. Confirm canonical CI is green at the chosen `main` SHA and write curated
   migration/release notes in the change history or release issue.
2. Dispatch `Release` with an unused version, that exact 40-character SHA, and
   curated consumer impact, deprecation, and migration notes.
3. Approve the protected environment only after reviewing the candidate job.
4. The publication job uploads a signed Central bundle with automatic
   publication disabled and stops. It first preserves the exact signed bundle
   in a draft release so an interrupted upload cannot be retried blindly.
   Review Central validation and publish that deployment manually.
5. Rerun the same version and revision. The workflow proves Central's anonymous
   bytes, rebuilds exact images, publishes or verifies the three GHCR tags,
   verifies anonymous digest pulls, creates attestations, pushes the immutable
   tag, and publishes the GitHub release.

The two dispatches are intentional. A Central validation or human approval
cannot be treated as an automatic deployment step.

### Recover A Partial Release

- If Central has no version, upload once as `USER_MANAGED`.
- If a draft has a signed bundle but no deployment ID, inspect the Publisher
  Portal and recover the existing deployment; never upload another one.
- If every Central component already exists with identical bytes, resume.
- If only part exists or any byte differs, stop and choose a new version.
- If a GHCR tag is absent, push it once.
- If it exists, resume only when its digest equals the prior release manifest.
- If the Git tag exists, it must peel to the requested revision.
- A draft GitHub release can be updated only for the same version, revision,
  bytes, and digests.

Never delete and recreate a public version. Never point an exact tag at new
bytes. Keep the failed run, Central deployment ID, and manifest with the release
incident record.

## Verification

Run the disposable local Maven proof without publication or signing credentials:

```bash
scripts/release/test-maven-release.sh 0.1.0-rc.0.local
scripts/release/test-compatibility-fixtures.sh
```

The first command deploys into a temporary file repository, filters it through
the release allowlist, creates a deterministic Central-layout bundle, and
compiles a standalone BOM consumer against a fresh Maven cache. A Maven Central
prerelease is permanent and must never be called disposable.

For the first external proof, publish a permanent prerelease such as
`0.1.0-rc.1`, make all three GHCR packages public, then verify from
signed-out/fresh environments:

- BOM import and all 13 JARs resolve anonymously from Maven Central;
- all three images pull by the manifest's digest without registry login;
- JAR and image version/revision metadata match the tag;
- checksums and attestations verify;
- a second dispatch cannot overwrite the release.

Issue #30 cannot close on repository changes alone: this permanent proof and
the repository protection prerequisites are external state.
