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
are retained publication evidence. The superseding protected verifier
[run 30027859272](https://github.com/lutzseverino/cardo/actions/runs/30027859272)
from trusted `main` revision
`3812c7f5145418d16922ba7d9696bcbe7bbd4ee2` is retained but insufficient
privacy evidence: its direct manifest `401 UNAUTHORIZED` responses stopped at
GHCR's initial authentication challenge. Its authenticated same-digest pulls
remain existence evidence. A new successful protected run that completes the
anonymous GHCR authorization flow is pending and must supersede it. These
publication proofs do not alone establish that `0.1.0-rc.3` is a known-good
production rollback target; select it only if the deployment repository has
approved environment-specific rollout and rollback evidence.

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
