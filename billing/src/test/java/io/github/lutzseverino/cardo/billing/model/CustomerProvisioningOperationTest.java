package io.github.lutzseverino.cardo.billing.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerProvisioningOperationTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-21T10:00:00Z");
  private static final UUID FIRST_LEASE = UUID.fromString("fd4f05d1-d4ce-4698-b874-a8898959d8f4");
  private static final UUID SECOND_LEASE = UUID.fromString("276c4c67-f2e6-458a-b3cc-b7f61f34e404");

  @Test
  void firstClaimDurablyMarksTheRemoteAttemptAndLaterClaimsRecover() {
    CustomerProvisioningOperation operation = operation();

    assertThat(operation.claim(FIRST_LEASE, NOW, Duration.ofMinutes(1))).isTrue();
    assertThat(operation.getRemoteAttemptedAt()).isEqualTo(NOW);
    assertThat(operation.ownsLease(FIRST_LEASE, NOW.plusSeconds(30))).isTrue();
    assertThat(operation.getNextAttemptAt()).isEqualTo(NOW.plusMinutes(1));

    assertThat(operation.claim(SECOND_LEASE, NOW.plusMinutes(2), Duration.ofMinutes(1))).isFalse();
    assertThat(operation.getRemoteAttemptedAt()).isEqualTo(NOW);
    assertThat(operation.ownsLease(FIRST_LEASE, NOW.plusMinutes(2))).isFalse();
    assertThat(operation.ownsLease(SECOND_LEASE, NOW.plusMinutes(2))).isTrue();
  }

  @Test
  void retryBackoffBecomesTerminalAtTheConfiguredBound() {
    CustomerProvisioningOperation operation = operation();

    operation.fail("temporary", NOW, Duration.ofSeconds(10), 2);

    assertThat(operation.getStatus()).isEqualTo(CustomerProvisioningStatus.REQUESTED);
    assertThat(operation.getAttemptCount()).isOne();
    assertThat(operation.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(10));

    operation.fail("still unavailable", NOW.plusSeconds(10), Duration.ofSeconds(10), 2);

    assertThat(operation.getStatus()).isEqualTo(CustomerProvisioningStatus.FAILED);
    assertThat(operation.getAttemptCount()).isEqualTo(2);
    assertThat(operation.getLastError()).isEqualTo("still unavailable");
  }

  @Test
  void completedOperationRetainsTheProviderCustomerForInspection() {
    CustomerProvisioningOperation operation = operation();

    operation.complete("cus_1", NOW);

    assertThat(operation.getStatus()).isEqualTo(CustomerProvisioningStatus.COMPLETED);
    assertThat(operation.getProviderCustomerId()).isEqualTo("cus_1");
    assertThat(operation.getCompletedAt()).isEqualTo(NOW);
  }

  private CustomerProvisioningOperation operation() {
    return CustomerProvisioningOperation.request(
        UUID.randomUUID(), UUID.randomUUID(), "stripe", NOW);
  }
}
