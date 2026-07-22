# Runtime Properties

This is the literal runtime contract for Cardo-owned services and published integrations. The
generated `META-INF/spring-configuration-metadata.json` in each owning artifact is the
machine-readable form. Environment variables in each service's `application.yml` are deployment
conveniences; the dotted names below are canonical.

`local` is the default service mode and permits disposable localhost dependencies. Production is
selected independently with `cardo.<service>.runtime.mode=production`. Invalid production input
fails startup before traffic is served, and errors name the property without echoing its value.

## Identity service

| Owner | Property | Type | Default | Production requirement | Secret | Validation |
| --- | --- | --- | --- | --- | --- | --- |
| Identity | `cardo.identity.runtime.mode` | enum | `local` | `production` | no | Selects fail-fast production policy. |
| Identity | `cardo.identity.runtime.connect-timeout` | duration | `2s` | optional | no | 1–2147483647 ms; bounds one Keycloak/JWK connection attempt. |
| Identity | `cardo.identity.runtime.read-timeout` | duration | `2s` | optional | no | 1–2147483647 ms; bounds one Keycloak/JWK response attempt. |
| Identity | `cardo.api.version` | string | `v1` | optional | no | API version path segment. |
| Identity | `cardo.api.base-path` | string | `/api/${cardo.api.version}` | optional | no | Base path used by security and OpenAPI routing. |
| Identity | `cardo.identity.datastore.database-name` | string | `cardo_identity` | required | no | Must equal `current_database()` at startup. |
| Identity | `cardo.identity.datastore.owner-role` | string | `cardo_identity_owner` | required | no | Must equal the PostgreSQL database owner and differ from the application role. |
| Identity | `cardo.identity.datastore.application-role` | string | `cardo_identity_app` | required | no | Must equal `current_user`; must not own the database. |
| Identity | `cardo.identity.session.mode` | enum | `local` | `production` | no | Production cookie policy must be selected. |
| Identity | `cardo.identity.session.access-cookie-name` | string | `cardo.session` | `__Host-cardo.session` | no | Required production host-prefixed access cookie. |
| Identity | `cardo.identity.session.refresh-cookie-name` | string | `cardo.refresh` | `__Secure-cardo.refresh` | no | Required production secure-prefixed refresh cookie. |
| Identity | `cardo.identity.session.csrf-cookie-name` | string | `cardo.csrf` | `__Host-cardo.csrf` | no | Required production host-prefixed CSRF cookie; distinct from session names. |
| Identity | `cardo.identity.session.refresh-cookie-path` | string | `${cardo.api.base-path}/identity/sessions/current` | required | no | Must be an absolute path. |
| Identity | `cardo.identity.session.secure` | boolean | `false` | `true` | no | All production session cookies must be Secure. |
| Identity | `cardo.identity.keycloak.base-url` | URI | `http://localhost:8080` | required | no | Remote HTTPS; canonical localhost, loopback, and unspecified literals rejected. |
| Identity | `cardo.identity.keycloak.realm` | string | `cardo` | required | no | Non-blank; forms the expected issuer. |
| Identity | `cardo.identity.keycloak.client-id` | string | `cardo-identity` | required | no | Non-blank runtime client identity. |
| Identity | `cardo.identity.keycloak.client-secret` | string | blank | required | yes | Non-blank, non-development credential. |
| Identity | `cardo.identity.keycloak.credential-setup-client-id` | string | `cardo-web` | required | no | Non-blank browser client. |
| Identity | `cardo.identity.keycloak.credential-setup-redirect-uri` | URI | `http://localhost:3000/invitations/completed` | required | no | Remote HTTPS; canonical local endpoints rejected. |
| Identity | `cardo.identity.keycloak.user-id-claim-client-ids` | list | `cardo-identity`, `identity`, `billing` | required | no | Non-empty, distinct, non-blank entries. |
| Identity | `cardo.identity.keycloak.legacy-startup-mutation-enabled` | boolean | `false` | `false` | no | Temporary migration seam. `false` validates read-only; `true` warns, repairs only canonical Identity mappers and roles, then validates. |
| Identity | `cardo.identity.operations.dispatch-delay` | duration | `5s` | optional | no | Positive scheduler delay. |
| Identity | `cardo.identity.operations.poll-delay` | duration | `15s` | optional | no | Positive completion poll delay. |
| Identity | `cardo.identity.operations.credential-setup-timeout` | duration | `24h` | optional | no | Positive provider action lifespan. |
| Identity | `cardo.identity.operations.retry-base-delay` | duration | `5s` | optional | no | Positive durable-work retry base. |
| Identity | `cardo.identity.operations.claim-lease` | duration | `1m` | optional | no | Positive worker lease. |
| Identity | `cardo.identity.operations.max-attempts` | integer | `12` | optional | no | At least 1. |
| Identity | `cardo.identity.operations.batch-size` | integer | `50` | optional | no | At least 1. |
| Identity | `cardo.identity.provider-mutations.dispatch-delay` | duration | `5s` | optional | no | Positive scheduler delay. |
| Identity | `cardo.identity.provider-mutations.retry-base-delay` | duration | `5s` | optional | no | Positive provider-mutation retry base. |
| Identity | `cardo.identity.provider-mutations.claim-lease` | duration | `1m` | optional | no | Positive mutation lease. |
| Identity | `cardo.identity.provider-mutations.max-attempts` | integer | `12` | optional | no | At least 1. |
| Identity | `cardo.identity.provider-mutations.batch-size` | integer | `50` | optional | no | At least 1. |
| Identity authorization | `spring.modulith.events.jdbc.schema` | string | `identity_events` | required | no | Event-publication schema used by grant receipts. |
| Identity authorization | `cardo.authorization.plans.max-attempts` | integer | `12` | optional | no | At least 1. |
| Identity authorization | `cardo.authorization.plans.retry-delay` | duration | `1m` | optional | no | Positive incomplete-plan recovery delay. |

## Invite service

| Owner | Property | Type | Default | Production requirement | Secret | Validation |
| --- | --- | --- | --- | --- | --- | --- |
| Invite | `cardo.invite.runtime.mode` | enum | `local` | `production` | no | Selects fail-fast production policy. |
| Invite | `cardo.invite.runtime.connect-timeout` | duration | `2s` | optional | no | 1–2147483647 ms; bounds one Keycloak/JWK connection attempt. |
| Invite | `cardo.invite.runtime.read-timeout` | duration | `2s` | optional | no | 1–2147483647 ms; bounds one Keycloak/JWK response attempt. |
| Invite | `cardo.api.version` | string | `v1` | optional | no | API version path segment. |
| Invite | `cardo.api.base-path` | string | `/api/${cardo.api.version}` | optional | no | Base path used by security and OpenAPI routing. |
| Invite | `cardo.invite.datastore.database-name` | string | `cardo_invite` | required | no | Must equal `current_database()` at startup. |
| Invite | `cardo.invite.datastore.owner-role` | string | `cardo_invite_owner` | required | no | Must equal the PostgreSQL database owner and differ from the application role. |
| Invite | `cardo.invite.datastore.application-role` | string | `cardo_invite_app` | required | no | Must equal `current_user`; must not own the database. |
| Invite | `cardo.invite.keycloak.base-url` | URI | `http://localhost:8080` | required | no | Remote HTTPS; canonical local endpoints rejected. |
| Invite | `cardo.invite.keycloak.realm` | string | `cardo` | required | no | Non-blank; forms the expected issuer. |
| Invite | `cardo.invite.keycloak.client-id` | string | `cardo-invite` | required | no | Non-blank exact Invite audience/client. |
| Invite | `cardo.invite.keycloak.client-secret` | string | blank | required | yes | Non-blank, non-development scoped credential. |
| Invite | `cardo.invite.product-callers.allowed-client-ids` | list | empty | required | no | Non-empty, distinct, non-blank product client IDs; duplicates survive binding and fail validation. |
| Invite | `cardo.invite.invitation.ttl` | duration | `72h` | optional | no | Positive invitation lifetime. |
| Invite | `cardo.invite.invitation.acceptance-clock-skew` | duration | `5m` | optional | no | Non-negative; zero disables skew allowance. |
| Invite | `cardo.invite.delivery.from` | string | `no-reply@localhost` | required | no | Non-blank SMTP From value. |
| Invite | `cardo.invite.delivery.retry-delay` | duration | `1m` | optional | no | Positive incomplete-delivery recovery delay. |
| Invite | `cardo.invite.smtp.connect-timeout` | duration | `2s` | optional | no | 1–2147483647 ms JavaMail connection bound. |
| Invite | `cardo.invite.smtp.read-timeout` | duration | `2s` | optional | no | 1–2147483647 ms JavaMail response bound. |
| Invite | `cardo.invite.smtp.write-timeout` | duration | `2s` | optional | no | 1–2147483647 ms JavaMail write bound. |
| Invite | `cardo.invite.completion.dispatch-delay` | duration | `5s` | optional | no | Positive scheduler delay. |
| Invite | `cardo.invite.completion.poll-delay` | duration | `15s` | optional | no | Positive completion poll delay. |
| Invite | `cardo.invite.completion.retry-base-delay` | duration | `5s` | optional | no | Positive durable-work retry base. |
| Invite | `cardo.invite.completion.claim-lease` | duration | `1m` | optional | no | Positive worker lease. |
| Invite | `cardo.invite.completion.max-attempts` | integer | `12` | optional | no | At least 1. |
| Invite | `cardo.invite.completion.batch-size` | integer | `50` | optional | no | At least 1. |
| Invite | `cardo.identity.client.base-url` | URI | `http://localhost:8081/api/v1` | required | no | Remote HTTP(S); canonical local endpoints rejected. |
| Invite | `cardo.identity.client.service-token-scope` | string | `identity` | required | no | Non-blank service-token scope. |
| Invite | `spring.mail.host` | string | `localhost` | required | no | Remote host; canonical localhost, loopback, and unspecified literals rejected. |
| Invite | `spring.mail.port` | integer | `1025` | required | no | 1–65535. |
| Invite | `spring.mail.username` | string | blank | required | no | Non-blank SMTP user. |
| Invite | `spring.mail.password` | string | blank | required | yes | Non-blank, non-development credential. |
| Invite | `spring.mail.properties.mail.smtp.auth` | boolean | `false` | `true` | no | SMTP authentication required. |
| Invite | `spring.mail.properties.mail.smtp.starttls.enable` | boolean | `false` | `true` | no | STARTTLS required. |
| Invite authorization | `spring.modulith.events.jdbc.schema` | string | `invite_events` | required | no | Event-publication schema used by grant receipts. |
| Invite authorization | `cardo.authorization.plans.max-attempts` | integer | `12` | optional | no | At least 1. |
| Invite authorization | `cardo.authorization.plans.retry-delay` | duration | `1m` | optional | no | Positive incomplete-plan recovery delay. |

## Billing service

| Owner | Property | Type | Default | Production requirement | Secret | Validation |
| --- | --- | --- | --- | --- | --- | --- |
| Billing | `cardo.billing.runtime.mode` | enum | `local` | `production` | no | Selects fail-fast production policy. |
| Billing | `cardo.billing.runtime.jwk-connect-timeout` | duration | `2s` | optional | no | 1–2147483647 ms; bounds one issuer/JWK connection attempt. |
| Billing | `cardo.billing.runtime.jwk-read-timeout` | duration | `2s` | optional | no | 1–2147483647 ms; bounds one issuer/JWK response attempt. |
| Billing | `cardo.api.version` | string | `v1` | optional | no | API version path segment. |
| Billing | `cardo.api.base-path` | string | `/api/${cardo.api.version}` | optional | no | Base path used by security and OpenAPI routing. |
| Billing | `cardo.billing.datastore.database-name` | string | `cardo_billing` | required | no | Must equal `current_database()` at startup. |
| Billing | `cardo.billing.datastore.owner-role` | string | `cardo_billing_owner` | required | no | Must equal the PostgreSQL database owner and differ from the application role. |
| Billing | `cardo.billing.datastore.application-role` | string | `cardo_billing_app` | required | no | Must equal `current_user`; must not own the database. |
| Billing | `cardo.billing.keycloak.base-url` | URI | `http://localhost:8080` | required | no | Remote HTTPS; canonical local endpoints rejected. |
| Billing | `cardo.billing.keycloak.realm` | string | `cardo` | required | no | Non-blank; forms the expected issuer. |
| Billing | `cardo.billing.stripe.secret-key` | string | blank | required | yes | Non-blank, non-development Stripe credential. |
| Billing | `cardo.billing.stripe.webhook-secret` | string | blank | required | yes | Non-blank, non-development signature credential. |
| Billing | `cardo.billing.stripe.checkout-prices` | list | empty | required | no | Non-empty; each entry has distinct non-blank `id` and `product`. |
| Billing | `cardo.billing.stripe.connect-timeout` | duration | `2s` | optional | no | 1–2147483647 ms; Stripe network retries fixed at zero. |
| Billing | `cardo.billing.stripe.read-timeout` | duration | `2s` | optional | no | 1–2147483647 ms; Stripe network retries fixed at zero. |
| Billing | `cardo.billing.customer-provisioning.dispatch-delay` | duration | `5s` | optional | no | Positive scheduler delay. |
| Billing | `cardo.billing.customer-provisioning.retry-base-delay` | duration | `10s` | optional | no | Positive durable-work retry base. |
| Billing | `cardo.billing.customer-provisioning.claim-lease` | duration | `1m` | optional | no | Positive worker lease. |
| Billing | `cardo.billing.customer-provisioning.max-attempts` | integer | `6` | optional | no | At least 1. |
| Billing | `cardo.billing.customer-provisioning.batch-size` | integer | `50` | optional | no | At least 1. |

## Standard datasource and issuer properties

| Owner | Property | Type | Default | Production requirement | Secret | Validation |
| --- | --- | --- | --- | --- | --- | --- |
| Identity | `spring.datasource.url` | JDBC URL | `jdbc:postgresql://localhost:5432/cardo` | required | no | Remote host; connected database must match `cardo.identity.datastore.database-name`. |
| Identity | `spring.datasource.username` | string | `cardo` | required | no | Effective role must match `cardo.identity.datastore.application-role`. |
| Identity | `spring.datasource.password` | string | `cardo` | required | yes | Non-blank, non-development credential. |
| Invite | `spring.datasource.url` | JDBC URL | `jdbc:postgresql://localhost:5432/cardo` | required | no | Remote host; connected database must match `cardo.invite.datastore.database-name`. |
| Invite | `spring.datasource.username` | string | `cardo` | required | no | Effective role must match `cardo.invite.datastore.application-role`. |
| Invite | `spring.datasource.password` | string | `cardo` | required | yes | Non-blank, non-development credential. |
| Billing | `spring.datasource.url` | JDBC URL | `jdbc:postgresql://localhost:5432/cardo` | required | no | Remote host; connected database must match `cardo.billing.datastore.database-name`. |
| Billing | `spring.datasource.username` | string | `cardo` | required | no | Effective role must match `cardo.billing.datastore.application-role`. |
| Billing | `spring.datasource.password` | string | `cardo` | required | yes | Non-blank, non-development credential. |
| Identity | `spring.security.oauth2.resourceserver.jwt.issuer-uri` | URI | Identity realm URI | required | no | Remote HTTPS and exact `<base-url>/realms/<realm>` match. |
| Invite | `spring.security.oauth2.resourceserver.jwt.issuer-uri` | URI | Invite realm URI | required | no | Remote HTTPS and exact `<base-url>/realms/<realm>` match. |
| Billing | `spring.security.oauth2.resourceserver.jwt.issuer-uri` | URI | Billing realm URI | required | no | Remote HTTPS and exact `<base-url>/realms/<realm>` match. |

Provision one database owner role without login and one login application role for each service.
Grant the application role only `CONNECT` and the schema/database privileges required for that
service's Flyway migrations and runtime queries. Revoke `CONNECT` from `PUBLIC` and grant no access
to the other service databases. Production startup runs Flyway first, then verifies
`current_database()`, `current_user`, the database owner, and that owner/application roles differ.
The repository integration tests exercise this policy and cross-owner denial for Identity, Invite,
and Billing. A deployment repository remains responsible for creating the databases, roles, grants,
and secrets; Cardo does not prescribe a host-per-service topology.

Identity constructs its decoder eagerly. Invite and Billing retain lazy issuer discovery, so an
issuer outage does not prevent public health startup; the first protected request fails closed
within the configured bounds.

## Published integrations

| Owner | Property | Type | Default | Requirement | Secret | Validation |
| --- | --- | --- | --- | --- | --- | --- |
| `identity-client-http` | `cardo.identity.client.base-url` | string | none | required | no | Non-blank. |
| `identity-client-http` | `cardo.identity.client.service-token-scope` | string | none | required | no | Non-blank and stripped. |
| `identity-client-http` | `cardo.identity.client.connect-timeout` | duration | `2s` | optional | no | 1–2147483647 ms. |
| `identity-client-http` | `cardo.identity.client.read-timeout` | duration | `2s` | optional | no | 1–2147483647 ms. |
| `invite-client-http` | `cardo.invite.client.base-url` | string | none | required | no | Non-blank. |
| `invite-client-http` | `cardo.invite.client.service-token-scope` | string | none | required | no | Non-blank and stripped. |
| `invite-client-http` | `cardo.invite.client.connect-timeout` | duration | `2s` | optional | no | 1–2147483647 ms. |
| `invite-client-http` | `cardo.invite.client.read-timeout` | duration | `2s` | optional | no | 1–2147483647 ms. |
| `billing-client-http` | `cardo.billing.client.base-url` | string | none | required | no | Non-blank. |
| `billing-client-http` | `cardo.billing.client.service-token-scope` | string | none | required | no | Non-blank and stripped. |
| `billing-client-http` | `cardo.billing.client.connect-timeout` | duration | `2s` | optional | no | 1–2147483647 ms. |
| `billing-client-http` | `cardo.billing.client.read-timeout` | duration | `2s` | optional | no | 1–2147483647 ms. |
| `identity-product-auth` | `cardo.identity.product-auth.session-cookie-name` | string | `cardo.session` | optional | no | Non-blank; distinct from CSRF name. |
| `identity-product-auth` | `cardo.identity.product-auth.csrf-cookie-name` | string | `cardo.csrf` | optional | no | Non-blank; distinct from session name. |
| `identity-product-auth` | `cardo.identity.product-auth.identity-session-audience` | string | none | required | no | Non-blank; distinct from product audience. |
| `identity-product-auth` | `cardo.identity.product-auth.product-audience` | string | none | required | no | Non-blank; distinct from Identity audience. |
| `identity-product-auth` | `cardo.identity.product-auth.token-exchange.connect-timeout` | duration | `2s` | optional | no | 1–2147483647 ms. |
| `identity-product-auth` | `cardo.identity.product-auth.token-exchange.read-timeout` | duration | `2s` | optional | no | 1–2147483647 ms; also bounds Nimbus issuer/JWK retrieval. |
| `identity-product-auth` | `cardo.identity.product-auth.active-token-validation.enabled` | boolean | `false` | optional | no | Enables per-request introspection. |
| `identity-product-auth` | `cardo.identity.product-auth.active-token-validation.introspection-uri` | URI | none | required when enabled | no | Must be present. |
| `identity-product-auth` | `cardo.identity.product-auth.active-token-validation.client-id` | string | none | required when enabled | no | Non-blank. |
| `identity-product-auth` | `cardo.identity.product-auth.active-token-validation.client-secret` | string | none | required when enabled | yes | Non-blank. |
| `identity-product-auth` | `cardo.identity.product-auth.active-token-validation.cache-ttl` | duration | `10s` | optional | no | Non-negative; zero disables positive-result reuse. |
| `identity-product-auth` | `cardo.identity.product-auth.active-token-validation.cache-max-entries` | integer | `2048` | optional | no | At least 1. |
| `identity-product-auth` | `cardo.identity.product-auth.active-token-validation.connect-timeout` | duration | `2s` | optional | no | 1–2147483647 ms. |
| `identity-product-auth` | `cardo.identity.product-auth.active-token-validation.read-timeout` | duration | `2s` | optional | no | 1–2147483647 ms. |
| `identity-product-auth` | `spring.security.oauth2.resourceserver.jwt.issuer-uri` | URI | none | required | no | Issuer used for signature validation and token exchange endpoint derivation. |
| `identity-product-auth` | `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` | URI | blank | optional | no | Overrides issuer discovery for JWK retrieval when non-blank. |

All network timeout values bound one synchronous attempt. Cardo adds no blanket HTTP, Stripe, SMTP,
or token retry layer around non-idempotent effects. Identity provider mutations, Invite completion,
authorization plans, and Billing customer provisioning remain the retry owners for durable work.
