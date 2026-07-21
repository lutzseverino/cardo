package io.github.lutzseverino.cardo.authorization.grant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GrantPlanListenerTest {

  private final GrantProcessor processor = mock(GrantProcessor.class);
  private final GrantReceiptStore receipts = mock(GrantReceiptStore.class);
  private final GrantReceiptProcessingLock processingLock = mock(GrantReceiptProcessingLock.class);
  private final GrantReceiptFailureRecorder failures = mock(GrantReceiptFailureRecorder.class);
  private final GrantPlanListener listener =
      new GrantPlanListener(processor, receipts, processingLock, failures, 3);

  GrantPlanListenerTest() {
    when(processingLock.tryAcquire(any())).thenReturn(true);
  }

  @Test
  void marksReceiptAppliedOnlyAfterProviderWorkCompletes() {
    StagedGrantPlan staged = staged();

    listener.apply(staged);

    verify(processor).apply(staged.plan());
    verify(receipts).markApplied(staged.receiptId());
    verifyNoInteractions(failures);
  }

  @Test
  void propagatesProviderFailureUntilTheAttemptLimit() {
    StagedGrantPlan staged = staged();
    IllegalStateException providerFailure = new IllegalStateException("provider unavailable");
    doThrow(providerFailure).when(processor).apply(staged.plan());
    when(failures.record(staged.receiptId(), 3)).thenReturn(false);

    assertThatThrownBy(() -> listener.apply(staged)).isSameAs(providerFailure);

    verify(failures).record(staged.receiptId(), 3);
    verify(receipts).find(staged.receiptId());
    verifyNoMoreInteractions(receipts);
  }

  @Test
  void completesPublicationAfterDurablyRecordingTerminalFailure() {
    StagedGrantPlan staged = staged();
    doThrow(new IllegalStateException("provider unavailable")).when(processor).apply(staged.plan());
    when(failures.record(staged.receiptId(), 3)).thenReturn(true);

    assertThatCode(() -> listener.apply(staged)).doesNotThrowAnyException();

    verify(failures).record(staged.receiptId(), 3);
    verify(receipts).find(staged.receiptId());
    verifyNoMoreInteractions(receipts);
  }

  @Test
  void doesNotMisclassifyReceiptPersistenceFailureAsProviderExhaustion() {
    StagedGrantPlan staged = staged();
    IllegalStateException persistenceFailure = new IllegalStateException("database unavailable");
    doThrow(persistenceFailure).when(receipts).markApplied(staged.receiptId());

    assertThatThrownBy(() -> listener.apply(staged)).isSameAs(persistenceFailure);

    verify(processor).apply(staged.plan());
    verifyNoInteractions(failures);
  }

  @Test
  void doesNotApplyProviderWorkForTerminalReceiptDuplicates() {
    StagedGrantPlan failed = staged(GrantReceiptStatus.FAILED);

    listener.apply(failed);

    verifyNoInteractions(processor, failures);
  }

  @Test
  void onlyOneOverlappingDeliveryCanApplyAReceipt() throws Exception {
    StagedGrantPlan staged = staged();
    AtomicBoolean held = new AtomicBoolean();
    when(processingLock.tryAcquire(staged.receiptId()))
        .thenAnswer(ignored -> held.compareAndSet(false, true));
    CountDownLatch providerStarted = new CountDownLatch(1);
    CountDownLatch releaseProvider = new CountDownLatch(1);
    doAnswer(
            ignored -> {
              providerStarted.countDown();
              if (!releaseProvider.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("provider test timed out");
              }
              return null;
            })
        .when(processor)
        .apply(staged.plan());

    AtomicReference<Throwable> firstFailure = new AtomicReference<>();
    Thread first =
        Thread.ofPlatform()
            .start(
                () -> {
                  try {
                    listener.apply(staged);
                  } catch (Throwable failure) {
                    firstFailure.set(failure);
                  }
                });
    assertThat(providerStarted.await(5, TimeUnit.SECONDS)).isTrue();

    assertThatThrownBy(() -> listener.apply(staged))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already being processed");

    releaseProvider.countDown();
    first.join(5_000);
    assertThat(first.isAlive()).isFalse();
    assertThat(firstFailure.get()).isNull();
    verify(processor).apply(staged.plan());
    verify(receipts).markApplied(staged.receiptId());
    verifyNoInteractions(failures);
  }

  private StagedGrantPlan staged() {
    return staged(GrantReceiptStatus.PENDING);
  }

  private StagedGrantPlan staged(GrantReceiptStatus status) {
    UUID receiptId = UUID.randomUUID();
    String failureCode =
        GrantReceiptStatus.FAILED.equals(status)
            ? GrantReceiptStore.PROVIDER_APPLICATION_FAILED
            : null;
    when(receipts.find(receiptId))
        .thenReturn(Optional.of(new GrantReceipt(receiptId, status, failureCode)));
    return new StagedGrantPlan(
        receiptId,
        new GrantPlan(
            List.of(),
            List.of(),
            List.of(new GrantPlan.AuthorityGrant("polity", "subject-1", List.of("member")))));
  }
}
