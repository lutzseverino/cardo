# Run The Portable Reference Stack

Use this procedure to exercise Cardo's real browser, product, service-client,
provider, persistence, and convergence boundaries from one checkout.

## Steps

1. Use Java 21 and start a Docker-compatible daemon that Testcontainers can
   reach. The script respects the selected Docker context and normalizes a
   non-default Unix socket for containers started inside that daemon.
2. From the repository root, run:

   ```bash
   ./scripts/smoke-reference-stack.sh
   ```

3. To reproduce the CI cleanup proof, run the same command a second time. Each
   invocation installs the Identity, Invite, Billing, and reference-stack
   reactor with upstream modules before launching checkout-built JARs. It does
   not depend on published Cardo snapshots or arbitrary pre-existing service
   artifacts.

Do not replace digest-pinned images, skip Docker-backed tests, or weaken the
production cookie, exact-audience, active-introspection, and least-privilege
checks to accommodate a local daemon problem.

## Verification

A successful run ends with Maven `BUILD SUCCESS`. On failure, inspect the
sanitized logs and milestone summary under
`integration/reference-stack/target/reference-stack/` together with the module's
Surefire and Failsafe reports. Diagnostics redact credentials, cookies, JWTs,
and invitation/action links.
