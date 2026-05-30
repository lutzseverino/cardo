# Authorization

`authorization` provides reusable authorization primitives for platform and product
services.

The module currently owns:

- authenticated user and permission helpers for Spring Security
- Keycloak authorization and token clients
- authorization resource and grant value objects
- access profile entities, repositories, and services
- authorization sync planning and processing

This is an embedded Java library boundary. It should only gain an OpenAPI contract
and generated HTTP clients if it is extracted into a service that owns HTTP
endpoints.
