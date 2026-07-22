# Runtime Properties

This is the indexed runtime contract for Cardo-owned services and published integrations. The
generated `META-INF/spring-configuration-metadata.json` in each owning artifact is the machine-readable
form. Environment variables shown in each service's `application.yml` are deployment conveniences;
the canonical names are the dotted properties below.

`local` mode is the default and deliberately supports disposable dependencies on localhost.
Production is selected independently by each process with `cardo.<service>.runtime.mode=production`.
It fails startup before traffic is served when required credentials, remote endpoints, issuer
relationships, allowlists, catalog entries, or datastore isolation inputs are unsafe. Validation
errors name the property and requirement but never echo its value.

## Service runtime policy

| Owner | Property | Type / default | Local / production | Secret | Purpose and validation |
| --- | --- | --- | --- | --- | --- |
| Identity | `cardo.identity.runtime.mode` | enum / `local` | optional / required `production` | no | Selects the explicit startup policy. |
| Identity | `cardo.identity.runtime.connect-timeout` | duration / `2s` | optional / optional | no | Positive connection bound for direct Keycloak user, session, admin, authorization, token, RPT, discovery, and JWK calls. |
| Identity | `cardo.identity.runtime.read-timeout` | duration / `2s` | optional / optional | no | Positive response bound for the same calls. One attempt only; workflow retry policy is unchanged. |
| Invite | `cardo.invite.runtime.mode` | enum / `local` | optional / required `production` | no | Selects Invite's startup policy. |
| Invite | `cardo.invite.runtime.connect-timeout` | duration / `2s` | optional / optional | no | Positive connection bound for Keycloak token, authorization, discovery, and JWK calls. |
| Invite | `cardo.invite.runtime.read-timeout` | duration / `2s` | optional / optional | no | Positive response bound for those calls. |
| Billing | `cardo.billing.runtime.mode` | enum / `local` | optional / required `production` | no | Selects Billing's startup policy. |
| Billing | `cardo.billing.runtime.jwk-connect-timeout` | duration / `2s` | optional / optional | no | Positive issuer-discovery and JWK connection bound. |
| Billing | `cardo.billing.runtime.jwk-read-timeout` | duration / `2s` | optional / optional | no | Positive issuer-discovery and JWK response bound. |

## Identity

| Property | Type / default | Local / production | Secret | Purpose and validation |
| --- | --- | --- | --- | --- |
| `cardo.identity.session.*` | mode, names, path, boolean / local cookie defaults | usable defaults / production mode and secure prefixed names required | no | Browser session and CSRF cookie contract; names must be distinct and refresh path absolute. |
| `cardo.identity.keycloak.base-url` | URI / `http://localhost:8080` | default / remote HTTPS required | no | Keycloak origin. |
| `cardo.identity.keycloak.realm` | string / `cardo` | default / non-blank | no | Realm segment and issuer relationship. |
| `cardo.identity.keycloak.client-id` | string / `cardo-identity` | default / non-blank | no | Runtime client identity. Privilege separation is owned by issue #37. |
| `cardo.identity.keycloak.client-secret` | string / blank | optional / required | yes | Runtime client credential; errors never include it. |
| `cardo.identity.keycloak.credential-setup-client-id` | string / `cardo-web` | default / non-blank | no | Browser client for credential setup. |
| `cardo.identity.keycloak.credential-setup-redirect-uri` | URI / localhost callback | default / remote HTTPS required | no | Allowed post-setup redirect. |
| `cardo.identity.keycloak.user-id-claim-client-ids` | list | configured / non-empty | no | Distinct non-blank clients receiving the Cardo user-id claim. |
| `cardo.identity.operations.dispatch-delay` | duration / `5s` | optional / optional | no | Positive scheduler delay. |
| `cardo.identity.operations.poll-delay` | duration / `15s` | optional / optional | no | Positive user-completion poll delay. |
| `cardo.identity.operations.credential-setup-timeout` | duration / `24h` | optional / optional | no | Positive provider action lifespan. |
| `cardo.identity.operations.retry-base-delay` | duration / `5s` | optional / optional | no | Positive durable-work retry base delay. |
| `cardo.identity.operations.claim-lease` | duration / `1m` | optional / optional | no | Positive worker lease. |
| `cardo.identity.operations.max-attempts`, `.batch-size` | integer / `12`, `50` | optional / optional | no | Positive attempt and batch bounds. |
| `cardo.identity.provider-mutations.dispatch-delay` | duration / `5s` | optional / optional | no | Positive #33 provider-mutation scheduler delay. |
| `cardo.identity.provider-mutations.retry-base-delay` | duration / `5s` | optional / optional | no | Positive retry delay; there is no separate provisional-creation retry setting. |
| `cardo.identity.provider-mutations.claim-lease` | duration / `1m` | optional / optional | no | Positive mutation lease. |
| `cardo.identity.provider-mutations.max-attempts`, `.batch-size` | integer / `12`, `50` | optional / optional | no | Positive mutation attempt and batch bounds. |

## Invite

| Property | Type / default | Local / production | Secret | Purpose and validation |
| --- | --- | --- | --- | --- |
| `cardo.invite.keycloak.base-url` | URI / localhost | default / remote HTTPS required | no | Keycloak origin. |
| `cardo.invite.keycloak.realm`, `.client-id` | string / `cardo`, `cardo-invite` | defaults / non-blank | no | Realm and exact Invite audience/client. |
| `cardo.invite.keycloak.client-secret` | string / blank | optional / required | yes | Scoped service credential. |
| `cardo.invite.product-callers.allowed-client-ids` | set / empty | optional / non-empty | no | Product caller boundary; blank and duplicate configured entries are rejected. |
| `cardo.invite.invitation.ttl` | duration / `72h` | optional / optional | no | Positive invitation lifetime. |
| `cardo.invite.invitation.acceptance-clock-skew` | duration / `5m` | optional / optional | no | Non-negative skew; zero explicitly disables skew allowance. |
| `cardo.invite.delivery.from` | string / `no-reply@localhost` | default / non-blank | no | SMTP From address. |
| `cardo.invite.smtp.connect-timeout`, `.read-timeout`, `.write-timeout` | duration / `2s` | optional / optional | no | Positive finite JavaMail bounds for one delivery attempt. |
| `cardo.invite.completion.dispatch-delay`, `.poll-delay`, `.retry-base-delay`, `.claim-lease` | duration / `5s`, `15s`, `5s`, `1m` | optional / optional | no | Positive durable completion workflow bounds. |
| `cardo.invite.completion.max-attempts`, `.batch-size` | integer / `12`, `50` | optional / optional | no | Positive workflow counts. |

Invite also owns these standard Spring properties: `spring.mail.host`, `.port`, `.username`,
`.password`, `.properties.mail.smtp.auth`, and `.properties.mail.smtp.starttls.enable`. Production
requires a remote host, port 1–65535, username, non-development secret, SMTP authentication, and
STARTTLS. `spring.mail.password` is secret. The Identity client used by Invite requires a remote
`cardo.identity.client.base-url` and non-blank `.service-token-scope` in production.

## Billing

| Property | Type / default | Local / production | Secret | Purpose and validation |
| --- | --- | --- | --- | --- |
| `cardo.billing.keycloak.base-url` | URI / localhost | default / remote HTTPS required | no | Issuer origin. |
| `cardo.billing.keycloak.realm` | string / `cardo` | default / non-blank | no | Realm and issuer relationship. |
| `cardo.billing.stripe.secret-key` | string / blank | optional / required | yes | Stripe API credential. |
| `cardo.billing.stripe.webhook-secret` | string / blank | optional / required | yes | Stripe signature credential. |
| `cardo.billing.stripe.checkout-prices` | list / empty | optional / non-empty | no | Each item requires distinct non-blank `id` and `product`. |
| `cardo.billing.stripe.connect-timeout`, `.read-timeout` | duration / `2s` | optional / optional | no | Positive millisecond-representable bounds. Stripe automatic network retries are fixed at zero. |
| `cardo.billing.customer-provisioning.dispatch-delay`, `.retry-base-delay`, `.claim-lease` | duration / `5s`, `10s`, `1m` | optional / optional | no | Positive durable provisioning bounds. |
| `cardo.billing.customer-provisioning.max-attempts`, `.batch-size` | integer / `6`, `50` | optional / optional | no | Positive workflow counts. |

## Datastore and issuer properties

Each service consumes `spring.datasource.url`, `.username`, and `.password`. Local mode retains the
shared disposable defaults. Production rejects localhost/the shared `cardo` database default, the
shared `cardo` role, and blank or development passwords. Deploy each service with its own database
and non-owner application role (or a service-owned schema with equivalent isolation). The role may
connect, migrate its own database/schema, and use only that owner's tables and event schema; it must
receive no rights on the other two owners. A deployment repository owns creating these databases,
roles, and secrets.

`spring.security.oauth2.resourceserver.jwt.issuer-uri` must be a remote HTTPS URI in production and
must equal `<service keycloak base-url>/realms/<realm>`. Identity constructs its decoder eagerly;
Invite and Billing retain lazy issuer discovery so an issuer outage does not prevent public health
startup, but the first protected request fails closed within the configured bounds.

## Published integration properties

| Owner | Properties | Defaults and validation |
| --- | --- | --- |
| `identity-client-http` | `cardo.identity.client.base-url`, `.service-token-scope`, `.connect-timeout`, `.read-timeout` | URL and scope required; positive timeout defaults `2s`. |
| `invite-client-http` | `cardo.invite.client.base-url`, `.service-token-scope`, `.connect-timeout`, `.read-timeout` | URL and scope required; positive timeout defaults `2s`. |
| `billing-client-http` | `cardo.billing.client.base-url`, `.service-token-scope`, `.connect-timeout`, `.read-timeout` | URL and scope required; positive timeout defaults `2s`. |
| `identity-product-auth` | cookie names, identity/product audiences, `token-exchange.connect-timeout/read-timeout`, active validation URI/client/secret/cache/timeout properties | Distinct cookie names and audiences; positive network bounds default `2s`; enabled introspection requires URI and credentials. The token-exchange bounds also bound its Nimbus issuer/JWK retrieval. Cache TTL may be zero to explicitly disable positive-result reuse; max entries remains positive. |

All timeout values bound one synchronous attempt. Cardo does not add HTTP, Stripe, SMTP, or token
retries around non-idempotent effects. Identity provider mutations, Invite completion, authorization
plans, and Billing customer provisioning remain the only retry owners for their durable work.
