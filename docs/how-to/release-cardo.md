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
   `GHCR_PUBLISH_TOKEN` for the `lutzseverino` registry account with
   `write:packages`; only the protected publish job uses it for GHCR package
   state reads and image pushes. The preflight resolves the token's current
   user and fails unless it is owned by `lutzseverino`. Add a separate
   `GHCR_PULL_TOKEN` with only `read:packages`; the protected verification job
   must not use its automatic `GITHUB_TOKEN` to pull Cardo packages.
4. Publish the signing public key through a durable public key service.
5. Protect `v*` tags, enable immutable GitHub releases, vulnerability alerts,
   and Dependabot security updates.
6. Keep `cardo/identity`, `cardo/invite`, and `cardo/billing` private in GHCR.
   Never change them to public: public container package visibility cannot be
   reverted. Keep them unlinked from every repository and do not grant the
   public `lutzseverino/cardo` repository Actions access to them.
7. Grant each authorized deployment repository package `Read` access. Its
   workflow should use its own job-scoped `GITHUB_TOKEN` with `packages: read`.

The release workflow never uses its automatic token to create, push, or inspect
runtime packages. The protected `GHCR_PUBLISH_TOKEN` owns those operations. A
fresh post-publication job has only `contents: read` on its automatic token and
uses the independently scoped `GHCR_PULL_TOKEN` to prove digest pulls and
anonymous rejection. No workflow changes package visibility or package
repository access.

Missing credentials, namespace ownership, private package visibility, alert
access, or repository protection must fail the release. Do not weaken a gate to
make the first release pass.

### Prepare And Run

1. Confirm canonical CI is green at the chosen `main` SHA and curate consumer
   impact, deprecation, and migration notes.
2. Dispatch `Release` with an unused SemVer, that exact 40-character SHA, and
   the curated notes.
3. Approve the protected environment after reviewing the candidate. Before
   signing or staging anything for Central, the workflow requires all three
   GHCR packages to be absent or private and none to be linked to a repository.
   It creates the deterministic annotated `v${version}` tag, or proves that an
   existing annotated tag peels to the requested revision, before any GitHub
   release is created. The first dispatch then preserves a signed Central
   bundle in a draft release, uploads it as `USER_MANAGED`, and stops for
   manual Central publication.
4. Publish that deployment in the Central Publisher Portal.
5. Rerun the exact version and revision. The workflow proves the anonymous
   Central bytes, rebuilds the candidate images, refuses any existing public or
   source-linked package, and pushes or verifies the exact private tags. The
   publish job records each service digest, immediately requires REST visibility
   `private` with no repository link, and logs out. A failure preserves every
   digest already recorded in the draft manifest and a focused Actions evidence
   artifact.
6. A subsequent fresh read-only package job requests a credential-free GHCR
   bearer token and requires an explicit `401 UNAUTHORIZED` response for every
   exact manifest digest. Network, registry, malformed, and other HTTP failures
   are not accepted as privacy evidence. It then proves authenticated digest
   pulls with the scoped token. Only then is the GitHub release made non-draft.

### Verify An Already-Published Release

When a non-draft release still needs fresh-runner private-runtime verification,
retain the original release run and dispatch the permanent read-only recovery
workflow with the published manifest's exact version and full source revision:

```bash
version=0.1.0-rc.3
revision=485f1d44f451ef2555ecea4cb3e3d051aad2a65c
gh workflow run verify-published-private-runtime.yml \
  --ref main \
  --field "version=$version" \
  --field "revision=$revision"
```

Approve the protected `release` environment, then retain the successful run URL
as release evidence. The workflow accepts only a non-draft `v${version}`
release, proves its manifest, tag, revision, and Central bundle identity, and
uses only `GHCR_PULL_TOKEN` for private digest pulls. It cannot publish or edit
a release, tag, or package. The revision input is evidence only: the job checks
out its trusted workflow-definition commit and runs the current validator and
verifier, never code selected by the historical revision. Keep the `release`
environment restricted to protected branches, and allow this manual verifier
only from protected `main`; that restriction is part of the `${{ github.sha }}`
trust invariant.

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
- An existing Git tag must be annotated and peel to the requested revision.
  The tag is established before the first staging draft, so neither staging nor
  resume can let GitHub synthesize a lightweight tag.
- A draft GitHub release can be updated only for the same version, revision,
  public bytes, and private digests.

Never delete and recreate a published version or retarget an exact tag. Retain
the failed run, Central deployment ID, and manifest with the incident record.
Any public runtime exposure is an incident and makes that version
non-resumable, even if later services were untouched. Deleting and recreating
the package is containment and namespace recovery only; it does not erase the
exposure or make the version releasable. Remediate the publication path and
publish a new, permanently successful prerelease before closing issue #30.

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

Issue #30 cannot close on repository changes alone. The exposed
`0.1.0-rc.1` is permanent incident history and cannot satisfy the release gate.
`0.1.0-rc.2` is also permanent, but only its BOM, libraries, and contracts were
published to Central. It has no private runtime images or digest-pull
verification, so it is non-deployable and non-resumable. Do not dispatch either
retired version. Publish `0.1.0-rc.3` from corrected `main` as the next complete
public-library/private-runtime prerelease, anonymously resolve its Central
artifacts, and complete private digest-pull verification for all three images.
