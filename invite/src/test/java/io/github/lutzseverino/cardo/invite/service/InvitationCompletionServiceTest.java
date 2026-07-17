package io.github.lutzseverino.cardo.invite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.invite.config.InvitationCompletionProperties;
import io.github.lutzseverino.cardo.invite.model.InvitationCompletionOperation;
import io.github.lutzseverino.cardo.invite.model.InvitationCompletionStatus;
import io.github.lutzseverino.cardo.invite.model.PendingInvitation;
import io.github.lutzseverino.cardo.invite.repository.InvitationCompletionOperationRepository;
import io.github.lutzseverino.cardo.invite.repository.InvitationRepository;
import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

class InvitationCompletionServiceTest {

  private static final UUID INVITATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final OffsetDateTime EXPIRES_AT = OffsetDateTime.parse("2030-07-17T10:00:00Z");

  @Test
  void requestUsesTheLockedInvitationLookupBeforeCreatingTheSingleOperation() {
    InvitationCompletionOperationRepository operations =
        mock(InvitationCompletionOperationRepository.class);
    InvitationService invitations = mock(InvitationService.class);
    PendingInvitation pending =
        new PendingInvitation(
            INVITATION_ID,
            "clinic",
            UUID.randomUUID(),
            "clinic:clinic",
            "clinic:employee",
            List.of(),
            USER_ID,
            "subject-1",
            EXPIRES_AT);
    when(invitations.requirePendingForUpdate("token-1", "clinic")).thenReturn(pending);
    when(operations.findById(INVITATION_ID)).thenReturn(Optional.empty());
    when(operations.saveAndFlush(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    assertThat(service(operations, invitations).request("token-1", "clinic").status())
        .isEqualTo(InvitationCompletionStatus.REQUESTED);

    verify(invitations).requirePendingForUpdate("token-1", "clinic");
  }

  @Test
  void terminalOperationCanBeReadWithoutRequiringAPendingInvitation() {
    InvitationCompletionOperationRepository operations =
        mock(InvitationCompletionOperationRepository.class);
    InvitationService invitations = mock(InvitationService.class);
    InvitationCompletionOperation operation =
        new InvitationCompletionOperation(
            INVITATION_ID, USER_ID, "clinic", EXPIRES_AT, OffsetDateTime.now());
    operation.failTerminal("Invitation expired before credential setup completed.", EXPIRES_AT);
    when(invitations.requireOwnedId("token-1", "clinic")).thenReturn(INVITATION_ID);
    when(operations.findById(INVITATION_ID)).thenReturn(Optional.of(operation));

    assertThat(service(operations, invitations).get("token-1", "clinic").status())
        .isEqualTo(InvitationCompletionStatus.FAILED);

    verify(invitations).requireOwnedId("token-1", "clinic");
    verify(invitations, never()).requirePending("token-1", "clinic");
  }

  @Test
  void invitationTokenLockIsPessimistic() throws Exception {
    Method method =
        InvitationRepository.class.getMethod("findEntityByTokenForUpdate", String.class);

    assertThat(method.getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
  }

  @Test
  void explicitRetryClearsTheExpiredProviderActionDeadline() {
    InvitationCompletionOperation operation =
        new InvitationCompletionOperation(
            INVITATION_ID, USER_ID, "clinic", EXPIRES_AT, OffsetDateTime.now());
    operation.awaitIdentity(OffsetDateTime.now(), EXPIRES_AT.minusDays(1));
    operation.failTerminal("Provider action expired.", OffsetDateTime.now());

    operation.retry(OffsetDateTime.now());

    assertThat(operation.getStatus()).isEqualTo(InvitationCompletionStatus.REQUESTED);
    assertThat(operation.getActionExpiresAt()).isNull();
  }

  private InvitationCompletionService service(
      InvitationCompletionOperationRepository operations, InvitationService invitations) {
    return new InvitationCompletionService(
        operations,
        new InvitationCompletionProperties(
            Duration.ofSeconds(5),
            Duration.ofSeconds(15),
            Duration.ofSeconds(5),
            Duration.ofMinutes(1),
            12,
            50),
        invitations);
  }
}
