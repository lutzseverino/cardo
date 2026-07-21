# Documentation

Documentation for the Invite HTTP Client project starts with its
[project overview](../README.md).

Every host application must configure the required
`cardo.invite.client.service-token-scope` property. Its default deployment value is
`cardo-invite`; the requested scope must emit exactly the Invite service's configured Keycloak
client ID as its sole audience. See the
[scoped service-token rollout](../../../docs/how-to/roll-out-scoped-service-tokens.md)
before enabling it in a deployed caller.

## Related Repository Documentation

- [Generated client code](../../../docs/reference/generated-client-code.md)
- [Product integration](../../../docs/reference/product-integration.md)
- [Cardo documentation](../../../docs/README.md)

Use the repository's canonical [documentation templates](../../../docs/_templates/README.md)
when adding project-specific documents here.
