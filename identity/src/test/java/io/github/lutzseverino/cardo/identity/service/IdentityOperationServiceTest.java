package io.github.lutzseverino.cardo.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.identity.config.IdentityOperationProperties;
import io.github.lutzseverino.cardo.identity.model.IdentityOperation;
import io.github.lutzseverino.cardo.identity.model.IdentityOperationStatus;
import io.github.lutzseverino.cardo.identity.model.IdentityOperationType;
import io.github.lutzseverino.cardo.identity.model.User;
import io.github.lutzseverino.cardo.identity.repository.IdentityOperationRepository;
import io.github.lutzseverino.cardo.identity.repository.UserRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class IdentityOperationServiceTest {

  private static final UUID OPERATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-17T10:00:00Z");
  private static final OffsetDateTime NOT_AFTER = OffsetDateTime.parse("2030-07-17T10:00:00Z");

  @Test
  void serializesCredentialSetupAndDeletionOnTheUserRow() {
    IdentityOperationRepository operations = mock(IdentityOperationRepository.class);
    UserRepository users = mock(UserRepository.class);
    User invited = User.invited("subject-1", "employee@example.com");
    when(users.findEntityByIdForUpdate(USER_ID)).thenReturn(Optional.of(invited));
    when(operations.findById(OPERATION_ID)).thenReturn(Optional.empty());
    when(operations.findEntityByUserIdAndType(USER_ID, IdentityOperationType.CREDENTIAL_SETUP))
        .thenReturn(Optional.empty());
    when(operations.findEntityByUserIdAndType(USER_ID, IdentityOperationType.PROVISIONAL_DELETION))
        .thenReturn(Optional.empty());
    when(operations.saveAndFlush(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service(operations, users).requestCredentialSetup(OPERATION_ID, USER_ID, NOT_AFTER);

    InOrder order = inOrder(users, operations);
    order.verify(users).findEntityByIdForUpdate(USER_ID);
    order.verify(operations).findById(OPERATION_ID);
  }

  @Test
  void failedDeletionStillBlocksCredentialSetup() {
    IdentityOperationRepository operations = mock(IdentityOperationRepository.class);
    UserRepository users = mock(UserRepository.class);
    IdentityOperation deletion = failedDeletion();
    when(users.findEntityByIdForUpdate(USER_ID))
        .thenReturn(Optional.of(User.invited("subject-1", "employee@example.com")));
    when(operations.findById(OPERATION_ID)).thenReturn(Optional.empty());
    when(operations.findEntityByUserIdAndType(USER_ID, IdentityOperationType.CREDENTIAL_SETUP))
        .thenReturn(Optional.empty());
    when(operations.findEntityByUserIdAndType(USER_ID, IdentityOperationType.PROVISIONAL_DELETION))
        .thenReturn(Optional.of(deletion));

    assertThatThrownBy(
            () ->
                service(operations, users).requestCredentialSetup(OPERATION_ID, USER_ID, NOT_AFTER))
        .isInstanceOf(ApiException.class)
        .hasMessage("Provisional identity deletion is pending.");

    verify(operations, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void failedDeletionCannotBeRetriedAfterTheUserBecameActive() {
    IdentityOperationRepository operations = mock(IdentityOperationRepository.class);
    UserRepository users = mock(UserRepository.class);
    IdentityOperation deletion = failedDeletion();
    User active = new User("subject-1", "employee@example.com", "Employee");
    when(operations.findEntityByUserIdAndType(USER_ID, IdentityOperationType.PROVISIONAL_DELETION))
        .thenReturn(Optional.of(deletion));
    when(users.findEntityByIdForUpdate(USER_ID)).thenReturn(Optional.of(active));

    assertThatThrownBy(() -> service(operations, users).requestProvisionalDeletion(USER_ID))
        .isInstanceOf(ApiException.class)
        .hasMessage("An active identity cannot be cancelled as provisional.");

    assertThat(deletion.getStatus()).isEqualTo(IdentityOperationStatus.FAILED);
  }

  @Test
  void completedDeletionRemainsIdempotentAfterTheLocalUserIsGone() {
    IdentityOperationRepository operations = mock(IdentityOperationRepository.class);
    UserRepository users = mock(UserRepository.class);
    IdentityOperation deletion =
        IdentityOperation.provisionalDeletion(OPERATION_ID, USER_ID, "subject-1", NOW);
    deletion.complete(NOW);
    when(operations.findEntityByUserIdAndType(USER_ID, IdentityOperationType.PROVISIONAL_DELETION))
        .thenReturn(Optional.of(deletion));

    assertThat(service(operations, users).requestProvisionalDeletion(USER_ID).status())
        .isEqualTo(IdentityOperationStatus.COMPLETED);

    verify(users, never()).findEntityByIdForUpdate(USER_ID);
  }

  private IdentityOperation failedDeletion() {
    IdentityOperation deletion =
        IdentityOperation.provisionalDeletion(OPERATION_ID, USER_ID, "subject-1", NOW);
    deletion.fail("provider unavailable", NOW, Duration.ofSeconds(1), 1);
    return deletion;
  }

  private IdentityOperationService service(
      IdentityOperationRepository operations, UserRepository users) {
    return new IdentityOperationService(
        operations,
        users,
        new IdentityOperationProperties(
            Duration.ofSeconds(5),
            Duration.ofSeconds(30),
            Duration.ofHours(24),
            Duration.ofSeconds(1),
            Duration.ofSeconds(30),
            5,
            25));
  }
}
