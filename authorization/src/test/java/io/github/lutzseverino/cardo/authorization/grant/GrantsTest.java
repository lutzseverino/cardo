package io.github.lutzseverino.cardo.authorization.grant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class GrantsTest {

  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final GrantReceiptStore receipts = mock(GrantReceiptStore.class);
  private final Grants grants = new Grants(events, receipts);

  @Test
  void publishesGrantPlan() {
    GrantPlan plan =
        new GrantPlan(
            List.of(),
            List.of(),
            List.of(
                new GrantPlan.AuthorityGrant("identity", "subject-1", List.of("profile:read"))));

    when(receipts.create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            invocation ->
                new GrantReceipt(invocation.getArgument(0), invocation.getArgument(1), null));

    GrantReceipt receipt = grants.stage(plan);

    ArgumentCaptor<StagedGrantPlan> event = ArgumentCaptor.forClass(StagedGrantPlan.class);
    verify(events).publishEvent(event.capture());
    assertThat(receipt.status()).isEqualTo(GrantReceiptStatus.PENDING);
    assertThat(event.getValue().receiptId()).isEqualTo(receipt.id());
    assertThat(event.getValue().plan()).isEqualTo(plan);
  }

  @Test
  void ignoresEmptyPlan() {
    when(receipts.create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            invocation ->
                new GrantReceipt(invocation.getArgument(0), invocation.getArgument(1), null));

    GrantReceipt receipt = grants.stage(new GrantPlan(List.of(), List.of(), List.of()));

    verifyNoInteractions(events);
    assertThat(receipt.status()).isEqualTo(GrantReceiptStatus.APPLIED);
  }

  @Test
  void findsDurableReceiptStatus() {
    UUID receiptId = UUID.randomUUID();
    GrantReceipt receipt = new GrantReceipt(receiptId, GrantReceiptStatus.APPLIED, null);
    when(receipts.find(receiptId)).thenReturn(Optional.of(receipt));

    assertThat(grants.find(receiptId)).contains(receipt);
  }
}
