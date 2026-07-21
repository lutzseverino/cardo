package io.github.lutzseverino.cardo.identity.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentityProviderMutationTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-22T10:00:00Z");

  @Test
  void staleEnabledTargetCannotBeAcknowledgedAsComplete() {
    IdentityProviderMutation mutation =
        IdentityProviderMutation.enabledState(
            UUID.randomUUID(), UUID.randomUUID(), "subject-1", false, NOW);
    UUID oldLease = mutation.claim(NOW.plusMinutes(1));

    mutation.changeEnabledTarget(true, NOW.plusSeconds(1));

    assertThat(mutation.complete(oldLease, 1, NOW.plusSeconds(2))).isFalse();
    assertThat(mutation.getStatus()).isEqualTo(IdentityProviderMutationStatus.REQUESTED);
    assertThat(mutation.getDesiredEnabled()).isTrue();
    assertThat(mutation.getDesiredVersion()).isEqualTo(2);
    assertThat(mutation.ready(NOW.plusSeconds(2))).isTrue();
  }

  @Test
  void boundedProvisionRecoveryEndsWithCredentialResubmission() {
    IdentityProviderMutation mutation =
        IdentityProviderMutation.passwordProvision(
            UUID.randomUUID(), "user@example.com", "User", "marker-1", NOW);
    RuntimeException missing = new IllegalStateException("not found by marker");

    UUID firstLease = mutation.claim(NOW.plusMinutes(1));
    assertThat(
            mutation.fail(
                firstLease,
                missing.getMessage(),
                NOW,
                Duration.ofSeconds(1),
                2,
                IdentityProviderMutationTerminalReason.CREDENTIAL_RESUBMISSION_REQUIRED))
        .isFalse();
    UUID secondLease = mutation.claim(NOW.plusMinutes(2));
    assertThat(
            mutation.fail(
                secondLease,
                missing.getMessage(),
                NOW.plusSeconds(1),
                Duration.ofSeconds(1),
                2,
                IdentityProviderMutationTerminalReason.CREDENTIAL_RESUBMISSION_REQUIRED))
        .isTrue();

    assertThat(mutation.getStatus()).isEqualTo(IdentityProviderMutationStatus.FAILED);
    assertThat(mutation.getTerminalReason())
        .isEqualTo(IdentityProviderMutationTerminalReason.CREDENTIAL_RESUBMISSION_REQUIRED);
  }

  @Test
  void provisioningIntentContainsNoPasswordField() {
    assertThat(
            java.util.Arrays.stream(IdentityProviderMutation.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
        .doesNotContain("password", "credential", "secret");
    assertThat(
            java.util.Arrays.stream(PasswordProvisioningIntent.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
        .doesNotContain("password", "credential", "secret");
  }
}
