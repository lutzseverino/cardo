package io.github.lutzseverino.cardo.identity.provider;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface IdentityProvider {

  ProvisionedIdentity provisionPasswordIdentity(
      String email, String password, String name, String correlationMarker);

  Optional<ProvisionedIdentity> findIdentityByCorrelationMarker(String correlationMarker);

  ProvisionedIdentity provisionProvisionalIdentity(String email, String correlationMarker);

  void requestCredentialSetup(String subject, Duration lifespan);

  Optional<CompletedIdentityProfile> completedIdentityProfile(String subject);

  void deleteIdentity(String subject);

  void bindUserId(String subject, UUID userId);

  void setIdentityEnabled(String subject, boolean enabled);

  IssuedSession issuePasswordSession(String email, String password);

  IssuedSession refreshSession(String refreshToken);

  void revokeSession(String refreshToken);

  record ProvisionedIdentity(String subject) {}

  record CompletedIdentityProfile(String name) {}

  record IssuedSession(
      String accessToken,
      OffsetDateTime accessExpiresAt,
      String refreshToken,
      OffsetDateTime refreshExpiresAt,
      String subject,
      String sessionId) {

    @Override
    public String toString() {
      return "IssuedSession[accessExpiresAt="
          + accessExpiresAt
          + ", refreshExpiresAt="
          + refreshExpiresAt
          + ", subject="
          + subject
          + ", sessionId="
          + sessionId
          + "]";
    }
  }
}
