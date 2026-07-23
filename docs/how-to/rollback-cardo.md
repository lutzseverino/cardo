# Roll Back A Cardo Release

Published Maven versions, Git tags, GitHub releases, and exact GHCR tags are
immutable. Rollback selects an older known-good release; it never mutates or
reuses the faulty version.

`0.1.0-rc.2` is public-Maven-only partial history, not a known-good deployable
release. It has no private image digest references, so it cannot be selected for
rollback or resumed.

`0.1.0-rc.3` is an immutable published prerelease from revision
`485f1d44f451ef2555ecea4cb3e3d051aad2a65c`. Its Central artifacts, private
runtime digests, and initial successful protected verifier
[run 30016640818](https://github.com/lutzseverino/cardo/actions/runs/30016640818)
are retained publication evidence. Earlier protected verifier
[run 30027859272](https://github.com/lutzseverino/cardo/actions/runs/30027859272)
is retained only as historical authenticated-pull existence evidence; its
direct anonymous manifest `401 UNAUTHORIZED` responses stopped at GHCR's
initial authentication challenge, so it is not the final privacy proof. Final
protected verifier
[run 30032236972](https://github.com/lutzseverino/cardo/actions/runs/30032236972)
from trusted verifier revision
`b7f0651a0ecc4e37c9d7dfdf1e97323c682ad8ae` is the canonical privacy and
same-digest pull evidence: it compiled a fresh standalone Java 21 consumer
against the public Cardo surface, confirmed that GHCR's token endpoint
explicitly denied anonymous requests for the exact three scoped repository
pulls without issuing an anonymous bearer token, and then authenticated with
the protected scoped credential and pulled those same three exact digests.
These publication proofs do not alone establish that `0.1.0-rc.3` is a
known-good production rollback target; select it only if the deployment
repository has approved environment-specific rollout and rollback evidence.

## Steps

1. Restore the product's previous `cardo-bom` version and rebuild it.
2. Deploy the previous `name@sha256:...` service references from that release's
   manifest.
3. Record both the Maven version and image digests in the deployment change.
4. Confirm that intervening database changes are backward-compatible, or use
   the deployment repository's tested database recovery procedure.
5. After containment, publish a new patch or prerelease from corrected `main`.

Deployment repositories own database compatibility, environment values,
traffic changes, and data rollback. Cardo publication automation does not roll
back a database or environment. Do not remove the superseded evidence: its
manifest, checksums, SBOMs, and attestations remain part of the audit trail.

## Verification

Confirm the running deployments report the selected older version and source
revision, and verify their pulled registry digests against that release's
manifest. Confirm rebuilt products resolved the intended BOM version from their
dependency reports.
