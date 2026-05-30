# Odonta Authorization

`authorization` is currently an embedded Java library boundary, not a standalone
HTTP service.

It owns reusable authorization primitives used by platform and product services:

- authenticated user and permission helpers for Spring Security
- Keycloak authorization and token clients
- authorization resource and grant value objects
- access profile entities, repositories, and services
- authorization sync planning and processing

Because this module does not own controllers or an HTTP runtime, it should not have
an OpenAPI spec or generated HTTP client yet. If authorization is later extracted
into a standalone platform service, that extraction should introduce the OpenAPI
contract, generated server interfaces, and generated client module in the same
pattern used by the HTTP-owning platform modules.
