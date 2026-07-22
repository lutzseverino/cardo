# Roll Back A Cardo Release

Published Maven versions, Git tags, GitHub releases, and exact GHCR tags are
immutable. Rollback selects an older known-good release; it never mutates or
reuses the faulty version.

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
