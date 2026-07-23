# OpenAPI Contract Changes

This reference defines the authoritative OpenAPI sources, runtime parity rules, and compatibility policy for Cardo HTTP APIs.

## Authoritative Documents

The canonical source documents are:

- `identity/openapi/identity.yaml`
- `invite/openapi/invite.yaml`
- `billing/openapi/billing.yaml`
- `common/openapi/errors.yaml`

CI validates and bundles all four documents before any compatibility comparison. The three service bundles are the consumer-facing contracts. Generated source remains derived output and must not be edited directly.

Each service also exposes its normalized runtime document at `/openapi.json`. Runtime parity tests remove the configured `/api/v1` server prefix and compare metadata, server URLs, security schemes and requirements, operations, parameters, request bodies, response media types, and response schemas with the canonical document.

Application-owned `401` and `403` failures pass through Cardo's exception handler and use the documented JSON `ApiError` representation. Authentication, authorization, or CSRF failures rejected earlier in the Spring Security filter chain may be bodyless. Consumers must branch on the HTTP status and tolerate an empty error body; when a JSON body is present, it follows `ApiError`.

The services deliberately have different public security boundaries:

- Identity browser mutations require both the Identity CSRF cookie and header. Current-session reads accept either bearer authentication or the Identity access cookie.
- Invite token inspection is public; invitation mutations and convergence operations remain authenticated as declared by the contract.
- The Billing Stripe webhook is public to bearer authentication because Stripe authenticates it with the provider signature. Billing product endpoints require bearer authentication.

## Pull-Request Compatibility

CI compares bundled service documents against the exact pull request base commit. Additive changes pass. Invalid references, invalid documents, and unapproved breaking changes fail.

An intentional breaking change must keep the compatibility report visible and include all of:

- `!` in the Conventional Commit pull-request title;
- a `BREAKING CHANGE:` footer describing consumer impact and migration;
- an `OpenAPI-Migration: #123` line that identifies the tracking issue.

These markers authorize review of the break; they do not hide or suppress the diff.

The current base-commit comparison is the available compatibility baseline because Cardo does not yet publish versioned contract bundles. Issue #30 must publish the bundled service documents and compare a proposed release with the last released bundle. The pull-request comparison should remain as early feedback.

## Internal Test Support

`openapi-support` attaches a `tests` classifier containing the shared runtime-parity assertion. It
is internal reactor test support and is denied from the public release surface. The contract-only
`cardo-openapi-contracts` artifact publishes the authoritative source documents byte-for-byte and
provides the durable compatibility baseline without generated or runtime helpers.
