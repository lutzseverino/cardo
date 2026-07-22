package io.github.lutzseverino.cardo.identity.config;

import io.github.lutzseverino.cardo.authorization.spring.CardoJwtClaims;
import io.github.lutzseverino.cardo.identity.IdentityPermissions;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

final class IdentityKeycloakProviderContract {

  static final String MAPPER_NAME = CardoJwtClaims.IDENTITY_USER_ID;
  static final List<String> IDENTITY_ROLES =
      List.of(
          IdentityPermissions.PROFILE_READ,
          IdentityPermissions.PROFILE_WRITE,
          IdentityPermissions.USER_PROVISION);

  private static final Map<String, String> MAPPER_CONFIG =
      Map.of(
          "user.attribute",
          CardoJwtClaims.IDENTITY_USER_ID,
          "claim.name",
          CardoJwtClaims.IDENTITY_USER_ID,
          "jsonType.label",
          "String",
          "access.token.claim",
          "true",
          "id.token.claim",
          "false",
          "userinfo.token.claim",
          "false",
          "multivalued",
          "false");

  private IdentityKeycloakProviderContract() {}

  static List<String> expectedClientIds(KeycloakProperties properties) {
    LinkedHashSet<String> clients = new LinkedHashSet<>();
    clients.add(properties.clientId());
    clients.add(properties.credentialSetupClientId());
    clients.addAll(mapperClientIds(properties));
    clients.add(IdentityPermissions.CLIENT_ID);
    return List.copyOf(clients);
  }

  static List<String> mapperClientIds(KeycloakProperties properties) {
    return properties.userIdClaimClientIds() == null
        ? List.of()
        : properties.userIdClaimClientIds().stream().distinct().toList();
  }

  static ProtocolMapper canonicalMapper() {
    return new ProtocolMapper(
        null,
        MAPPER_NAME,
        "openid-connect",
        "oidc-usermodel-attribute-mapper",
        false,
        MAPPER_CONFIG);
  }

  static boolean isCanonical(ProtocolMapper mapper) {
    return MAPPER_NAME.equals(mapper.name())
        && "openid-connect".equals(mapper.protocol())
        && "oidc-usermodel-attribute-mapper".equals(mapper.protocolMapper())
        && !Boolean.TRUE.equals(mapper.consentRequired())
        && mapper.config() != null
        && MAPPER_CONFIG.entrySet().stream()
            .allMatch(entry -> entry.getValue().equals(mapper.config().get(entry.getKey())));
  }

  record ClientRepresentation(String id, String clientId) {}

  record ProtocolMapper(
      String id,
      String name,
      String protocol,
      String protocolMapper,
      Boolean consentRequired,
      Map<String, String> config) {

    ProtocolMapper withId(String id) {
      return new ProtocolMapper(id, name, protocol, protocolMapper, consentRequired, config);
    }
  }

  record RoleRepresentation(String id, String name) {}
}
