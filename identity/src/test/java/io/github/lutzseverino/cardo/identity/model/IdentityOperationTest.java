package io.github.lutzseverino.cardo.identity.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentityOperationTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-17T10:00:00Z");

  @Test
  void expiredCredentialSetupCanBeExplicitlyRestarted() {
    IdentityOperation operation =
        IdentityOperation.credentialSetup(
            UUID.randomUUID(), UUID.randomUUID(), "subject-1", NOW.plusDays(3), NOW);
    operation.awaitUser(NOW.plusSeconds(15), NOW.plusHours(24));

    assertThat(operation.credentialSetupExpired(NOW.plusHours(24))).isTrue();

    operation.expire("Credential setup expired before completion.", NOW.plusHours(24));
    assertThat(operation.getStatus()).isEqualTo(IdentityOperationStatus.FAILED);

    operation.retry(NOW.plusHours(25));
    assertThat(operation.getStatus()).isEqualTo(IdentityOperationStatus.REQUESTED);
    assertThat(operation.getExpiresAt()).isNull();
  }

  @Test
  void exhaustedTransientFailuresRemainInspectableUntilExplicitRetry() {
    IdentityOperation operation =
        IdentityOperation.provisionalDeletion(
            UUID.randomUUID(), UUID.randomUUID(), "subject-1", NOW);

    operation.fail("provider unavailable", NOW, Duration.ofSeconds(5), 2);
    operation.fail("provider unavailable", NOW.plusSeconds(5), Duration.ofSeconds(5), 2);

    assertThat(operation.getStatus()).isEqualTo(IdentityOperationStatus.FAILED);
    assertThat(operation.getAttemptCount()).isEqualTo(2);
    assertThat(operation.getLastError()).isEqualTo("provider unavailable");
  }
}
