# Release Cardo

Only repository maintainers should run the protected manual `Release` workflow.
It publishes the product-integration surface to Maven Central and the three
service images privately to GHCR under one version and source revision.

## Steps

### Configure One-Time Prerequisites

1. Sign in to the Maven Central Publisher Portal with the `lutzseverino` GitHub
   account. Confirm the verified parent `io.github.lutzseverino` authorizes the
   child group ID `io.github.lutzseverino.cardo`; contact Central Support if the
   parent is absent.
2. Create the protected GitHub `release` environment with required reviewers.
3. Add `CENTRAL_TOKEN_USERNAME`, `CENTRAL_TOKEN_PASSWORD`, `GPG_PRIVATE_KEY`,
   and `GPG_PASSPHRASE` only to that environment. Add a dedicated
   `GHCR_PULL_TOKEN` for the `lutzseverino` registry account with only
   `read:packages`; the protected verification job must not use its automatic
   `GITHUB_TOKEN` to pull Cardo packages.
4. Publish the signing public key through a durable public key service.
5. Protect `v*` tags, enable immutable GitHub releases, vulnerability alerts,
   and Dependabot security updates.
6. Keep `cardo/identity`, `cardo/invite`, and `cardo/billing` private in GHCR.
   Never change them to public: public container package visibility cannot be
   reverted.
7. Grant each authorized deployment repository package `Read` access. Its
   workflow should use its own job-scoped `GITHUB_TOKEN` with `packages: read`.

The release workflow uses its write-scoped job token only to push. A fresh
post-publication job has only `contents: read` on its automatic token and uses
the independently scoped `GHCR_PULL_TOKEN` to prove digest pulls and anonymous
rejection. No workflow changes package visibility.

Missing credentials, namespace ownership, private package visibility, alert
access, or repository protection must fail the release. Do not weaken a gate to
make the first release pass.

### Prepare And Run

1. Confirm canonical CI is green at the chosen `main` SHA and curate consumer
   impact, deprecation, and migration notes.
2. Dispatch `Release` with an unused SemVer, that exact 40-character SHA, and
   the curated notes.
3. Approve the protected environment after reviewing the candidate. The first
   dispatch preserves a signed Central bundle in a draft release, uploads it as
   `USER_MANAGED`, and stops for manual Central publication.
4. Publish that deployment in the Central Publisher Portal.
5. Rerun the exact version and revision. The workflow proves the anonymous
   Central bytes, rebuilds the candidate images, refuses any existing public
   package, pushes or verifies the exact private tags, and immediately requires
   REST visibility `private`.
6. A fresh read-only package job verifies that anonymous digest pulls fail and
   authenticated digest pulls succeed. Only then is the GitHub release made
   non-draft.

### Recover A Partial Release

- If Central has no version, upload once as `USER_MANAGED`.
- If a draft has a signed bundle but no deployment ID, inspect the Publisher
  Portal; never upload another deployment automatically.
- If every Central component exists with identical bytes, resume. Partial or
  different bytes require a new version.
- If a private GHCR tag is absent, push it once. If it exists with a recorded
  digest, require exact digest equality. If a runner stopped before recording
  the digest, pull the existing private tag and resume only when its Docker
  content ID equals the freshly rebuilt and validated candidate.
- An existing Git tag must peel to the requested revision.
- A draft GitHub release can be updated only for the same version, revision,
  public bytes, and private digests.

Never delete and recreate a published version or retarget an exact tag. Retain
the failed run, Central deployment ID, and manifest with the incident record.

## Verification

Run the local public candidate and policy fixtures without publication or
signing credentials:

```bash
scripts/release/test-maven-release.sh 0.1.0-rc.0.local
scripts/release/test-compatibility-fixtures.sh
scripts/release/test-private-images.sh
```

The Maven fixture stages only the public allowlist, proves the private denylist,
checks self-contained POMs and exact contract resources, and compiles a clean
standalone product consumer. A Maven Central prerelease is permanent and must
never be described as disposable.

Issue #30 cannot close on repository changes alone. Publish a permanent
prerelease such as `0.1.0-rc.1`, anonymously resolve its BOM, libraries, and
contracts from Central, and complete private digest-pull verification for all
three images.
