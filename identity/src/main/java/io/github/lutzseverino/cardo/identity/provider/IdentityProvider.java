package io.github.lutzseverino.cardo.identity.provider;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface IdentityProvider {

  ProvisionedIdentity provisionPasswordIdentity(String email, String password, String name);

  ProvisionedIdentity provisionProvisionalIdentity(String email);

  void completePasswordIdentity(String subject, String password, String name);

  void deleteIdentity(String subject);

  void bindUserId(String subject, UUID userId);

  void setIdentityEnabled(String subject, boolean enabled);

  void ensureUserIdClaimMapped(List<String> clientIds);

  IssuedIdentityToken issuePasswordToken(String email, String password);

  void revokeToken(String token);

  record ProvisionedIdentity(String subject) {}

  record IssuedIdentityToken(
      String token, OffsetDateTime expiresAt, String subject, String sessionId) {}
}
