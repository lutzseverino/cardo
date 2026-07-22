package io.github.lutzseverino.cardo.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.authorization.grant.Grants;
import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.common.model.FieldUpdate;
import io.github.lutzseverino.cardo.identity.IdentityPermissions;
import io.github.lutzseverino.cardo.identity.authorization.IdentityGrantPlanner;
import io.github.lutzseverino.cardo.identity.mapper.UserApplicationMapperImpl;
import io.github.lutzseverino.cardo.identity.model.CreateProvisionalUserInput;
import io.github.lutzseverino.cardo.identity.model.CreateUserInput;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutationTerminalReason;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutationType;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutationWork;
import io.github.lutzseverino.cardo.identity.model.PasswordProvisioningIntent;
import io.github.lutzseverino.cardo.identity.model.UpdateCurrentUserInput;
import io.github.lutzseverino.cardo.identity.model.UpdateUserInput;
import io.github.lutzseverino.cardo.identity.model.User;
import io.github.lutzseverino.cardo.identity.model.UserResult;
import io.github.lutzseverino.cardo.identity.model.UserStatus;
import io.github.lutzseverino.cardo.identity.provider.IdentityProvider;
import io.github.lutzseverino.cardo.identity.repository.UserProjection;
import io.github.lutzseverino.cardo.identity.repository.UserRepository;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserRepository users;
  @Mock private IdentityProvider identityProvider;
  @Mock private IdentityProviderMutationService providerMutations;
  @Mock private Grants grants;
  @Mock private TransactionOperations transactions;

  @Test
  void persistsProvisioningIntentBeforeUsingTheRequestPasswordOnce() {
    UserService service = service();
    UUID userId = UUID.randomUUID();
    PasswordProvisioningIntent intent = intent();
    when(users.findProjectedByEmail("owner@example.com")).thenReturn(Optional.empty());
    when(providerMutations.requestPasswordProvision("owner@example.com", "Owner"))
        .thenReturn(intent);
    when(identityProvider.provisionPasswordIdentity(
            "owner@example.com", "password-1", "Owner", "correlation-1"))
        .thenReturn(new IdentityProvider.ProvisionedIdentity("kc-user-1"));
    when(users.saveAndFlush(any(User.class)))
        .thenAnswer(invocation -> persisted(invocation, userId));
    when(users.findProjectedById(userId)).thenReturn(Optional.of(projection(UserStatus.ACTIVE)));

    assertThat(service.create(new CreateUserInput("owner@example.com", "password-1", "Owner")))
        .extracting(UserResult::status)
        .isEqualTo(UserStatus.ACTIVE);

    InOrder order = inOrder(providerMutations, identityProvider, users);
    order.verify(providerMutations).requestPasswordProvision("owner@example.com", "Owner");
    order
        .verify(identityProvider)
        .provisionPasswordIdentity("owner@example.com", "password-1", "Owner", "correlation-1");
    order.verify(users).saveAndFlush(any(User.class));
    order.verify(providerMutations).completePasswordProvision(intent, "kc-user-1", userId);
  }

  @Test
  void recoversAnAmbiguousProvisioningResponseByCorrelationMarker() {
    UserService service = service();
    UUID userId = UUID.randomUUID();
    PasswordProvisioningIntent intent = intent();
    when(users.findProjectedByEmail("owner@example.com")).thenReturn(Optional.empty());
    when(providerMutations.requestPasswordProvision("owner@example.com", "Owner"))
        .thenReturn(intent);
    when(identityProvider.provisionPasswordIdentity(
            "owner@example.com", "password-1", "Owner", "correlation-1"))
        .thenThrow(new RuntimeException("response lost"));
    when(identityProvider.findIdentityByCorrelationMarker("correlation-1"))
        .thenReturn(Optional.of(new IdentityProvider.ProvisionedIdentity("kc-user-1")));
    when(users.saveAndFlush(any(User.class)))
        .thenAnswer(invocation -> persisted(invocation, userId));
    when(users.findProjectedById(userId)).thenReturn(Optional.of(projection(UserStatus.ACTIVE)));

    service.create(new CreateUserInput("owner@example.com", "password-1", "Owner"));

    verify(identityProvider).findIdentityByCorrelationMarker("correlation-1");
    verify(providerMutations).completePasswordProvision(intent, "kc-user-1", userId);
  }

  @Test
  void terminalsAndDeletesAnUnboundPasswordIdentityAfterALateEmailConflict() {
    UserService service = service();
    PasswordProvisioningIntent intent = intent();
    UserProjection conflicting = projection(UserStatus.ACTIVE);
    when(users.findProjectedByEmail("owner@example.com"))
        .thenReturn(Optional.empty(), Optional.of(conflicting));
    when(providerMutations.requestPasswordProvision("owner@example.com", "Owner"))
        .thenReturn(intent);
    when(identityProvider.provisionPasswordIdentity(
            "owner@example.com", "password-1", "Owner", "correlation-1"))
        .thenReturn(new IdentityProvider.ProvisionedIdentity("kc-new-user"));
    when(providerMutations.recordPasswordCompletionConflict(any(), any())).thenReturn(true);
    when(users.findProjectedByKeycloakSubject("kc-new-user")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.create(new CreateUserInput("owner@example.com", "password-1", "Owner")))
        .isInstanceOfSatisfying(
            ApiException.class,
            conflict -> {
              assertThat(conflict.status()).isEqualTo(409);
              assertThat(conflict.code()).isEqualTo("user_exists");
            });

    verify(providerMutations).recordPasswordCompletionConflict(eq(intent), any(ApiException.class));
    verify(identityProvider).deleteIdentity("kc-new-user");
    verify(providerMutations, never()).completePasswordProvision(any(), any(), any());
  }

  @Test
  void allowsCredentialResubmissionAfterTheProviderDefinitelyRejectsThePassword() {
    UserService service = service();
    PasswordProvisioningIntent intent = intent();
    ApiException rejected =
        ApiException.of(400, "identity_provider_error", "Identity provider request failed.");
    when(users.findProjectedByEmail("owner@example.com")).thenReturn(Optional.empty());
    when(providerMutations.requestPasswordProvision("owner@example.com", "Owner"))
        .thenReturn(intent);
    when(identityProvider.provisionPasswordIdentity(
            "owner@example.com", "invalid-password", "Owner", "correlation-1"))
        .thenThrow(rejected);
    when(identityProvider.findIdentityByCorrelationMarker("correlation-1"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.create(
                    new CreateUserInput("owner@example.com", "invalid-password", "Owner")))
        .isSameAs(rejected);

    verify(providerMutations)
        .recordPasswordDispatchRejection(
            intent,
            rejected,
            IdentityProviderMutationTerminalReason.CREDENTIAL_RESUBMISSION_REQUIRED);
  }

  @Test
  void treatsAConflictWithNoVisibleMarkerAsRecoverable() {
    UserService service = service();
    PasswordProvisioningIntent intent = intent();
    ApiException conflict = ApiException.conflict("user_exists", "User already exists.");
    when(users.findProjectedByEmail("owner@example.com")).thenReturn(Optional.empty());
    when(providerMutations.requestPasswordProvision("owner@example.com", "Owner"))
        .thenReturn(intent);
    when(identityProvider.provisionPasswordIdentity(
            "owner@example.com", "password-2", "Owner", "correlation-1"))
        .thenThrow(conflict);
    when(identityProvider.findIdentityByCorrelationMarker("correlation-1"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.create(new CreateUserInput("owner@example.com", "password-2", "Owner")))
        .isSameAs(conflict);

    verify(providerMutations).recordPasswordDispatchFailure(intent, conflict);
    verify(providerMutations, never())
        .recordPasswordDispatchRejection(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void reusesExistingProvisionalUser() {
    UserProjection existing = projection(UserStatus.INVITED);
    UserService service = service();
    when(users.findProjectedByEmail("employee@example.com")).thenReturn(Optional.of(existing));

    assertThat(service.createProvisional(new CreateProvisionalUserInput("employee@example.com")))
        .extracting(result -> result.id(), result -> result.status())
        .containsExactly(existing.getId(), UserStatus.INVITED);

    verify(identityProvider, never()).provisionProvisionalIdentity(any(), any());
  }

  @Test
  void persistsProvisionalIntentBeforeProviderEffectAndStagesBindingAndGrants() {
    UserService service = service();
    UUID userId = UUID.randomUUID();
    IdentityProviderMutationWork work = provisionalWork();
    when(users.findProjectedByEmail("employee@example.com")).thenReturn(Optional.empty());
    when(providerMutations.requestProvisionalProvision("employee@example.com")).thenReturn(work);
    when(identityProvider.provisionProvisionalIdentity("employee@example.com", "marker-1"))
        .thenReturn(new IdentityProvider.ProvisionedIdentity("subject-1"));
    when(users.saveAndFlush(any(User.class)))
        .thenAnswer(invocation -> persisted(invocation, userId));
    when(users.findProjectedById(userId))
        .thenReturn(
            Optional.of(
                projection(userId, "subject-1", "employee@example.com", UserStatus.INVITED)));

    assertThat(service.createProvisional(new CreateProvisionalUserInput("employee@example.com")))
        .extracting(UserResult::id, UserResult::status)
        .containsExactly(userId, UserStatus.INVITED);

    InOrder order = inOrder(providerMutations, identityProvider, users);
    order.verify(providerMutations).requestProvisionalProvision("employee@example.com");
    order.verify(identityProvider).provisionProvisionalIdentity("employee@example.com", "marker-1");
    order.verify(users).saveAndFlush(any(User.class));
    order.verify(providerMutations).completeProvisionalProvision(work, "subject-1", userId);
    verify(grants).stage(any());
    verify(identityProvider, never()).bindUserId(any(), any());
    verify(identityProvider, never()).deleteIdentity(any());
  }

  @Test
  void recoversLostProvisionalCreateResponseOnlyByExactMarker() {
    UserService service = service();
    UUID userId = UUID.randomUUID();
    IdentityProviderMutationWork work = provisionalWork();
    when(users.findProjectedByEmail("employee@example.com")).thenReturn(Optional.empty());
    when(providerMutations.requestProvisionalProvision("employee@example.com")).thenReturn(work);
    when(identityProvider.provisionProvisionalIdentity("employee@example.com", "marker-1"))
        .thenThrow(new RuntimeException("response lost"));
    when(identityProvider.findIdentityByCorrelationMarker("marker-1"))
        .thenReturn(Optional.of(new IdentityProvider.ProvisionedIdentity("subject-1")));
    when(users.saveAndFlush(any(User.class)))
        .thenAnswer(invocation -> persisted(invocation, userId));
    when(users.findProjectedById(userId))
        .thenReturn(
            Optional.of(
                projection(userId, "subject-1", "employee@example.com", UserStatus.INVITED)));

    service.createProvisional(new CreateProvisionalUserInput("employee@example.com"));

    verify(identityProvider).findIdentityByCorrelationMarker("marker-1");
    verify(providerMutations).completeProvisionalProvision(work, "subject-1", userId);
    verify(identityProvider, never()).deleteIdentity(any());
  }

  @Test
  void doesNotAdoptOrDeleteAnUnattributedSameEmailProviderIdentity() {
    UserService service = service();
    IdentityProviderMutationWork work = provisionalWork();
    ApiException conflict = ApiException.conflict("user_exists", "User already exists.");
    when(users.findProjectedByEmail("employee@example.com")).thenReturn(Optional.empty());
    when(providerMutations.requestProvisionalProvision("employee@example.com")).thenReturn(work);
    when(identityProvider.provisionProvisionalIdentity("employee@example.com", "marker-1"))
        .thenThrow(conflict);
    when(identityProvider.findIdentityByCorrelationMarker("marker-1")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.createProvisional(new CreateProvisionalUserInput("employee@example.com")))
        .isSameAs(conflict);

    verify(providerMutations)
        .recordTerminalFailure(
            work, conflict, IdentityProviderMutationTerminalReason.PROVIDER_REJECTED);
    verify(users, never()).saveAndFlush(any());
    verify(identityProvider, never()).deleteIdentity(any());
  }

  @Test
  void terminalsALocalConflictWithoutDeletingTheMarkerOwnedIdentity() {
    UserService service = service();
    IdentityProviderMutationWork work = provisionalWork();
    UserProjection conflicting =
        projection(
            UUID.randomUUID(), "unrelated-subject", "employee@example.com", UserStatus.ACTIVE);
    when(users.findProjectedByEmail("employee@example.com"))
        .thenReturn(Optional.empty(), Optional.of(conflicting));
    when(providerMutations.requestProvisionalProvision("employee@example.com")).thenReturn(work);
    when(identityProvider.provisionProvisionalIdentity("employee@example.com", "marker-1"))
        .thenReturn(new IdentityProvider.ProvisionedIdentity("subject-1"));

    assertThatThrownBy(
            () -> service.createProvisional(new CreateProvisionalUserInput("employee@example.com")))
        .isInstanceOfSatisfying(
            ApiException.class,
            failure -> {
              assertThat(failure.status()).isEqualTo(409);
              assertThat(failure.code()).isEqualTo("user_exists");
            });

    verify(providerMutations)
        .recordTerminalFailure(
            eq(work),
            any(ApiException.class),
            eq(IdentityProviderMutationTerminalReason.LOCAL_STATE_CONFLICT));
    verify(identityProvider, never()).deleteIdentity(any());
    verify(identityProvider, never()).bindUserId(any(), any());
  }

  @Test
  void convergesAConcurrentLocalInsertOwnedByTheSameMarkerIdentity() {
    UserService service = service();
    UUID userId = UUID.randomUUID();
    IdentityProviderMutationWork work = provisionalWork();
    UserProjection existing =
        projection(userId, "subject-1", "employee@example.com", UserStatus.INVITED);
    User owned = User.invited("subject-1", "employee@example.com");
    ReflectionTestUtils.setField(owned, "id", userId);
    when(users.findProjectedByEmail("employee@example.com"))
        .thenReturn(
            Optional.empty(), Optional.empty(), Optional.of(existing), Optional.of(existing));
    when(providerMutations.requestProvisionalProvision("employee@example.com")).thenReturn(work);
    when(identityProvider.provisionProvisionalIdentity("employee@example.com", "marker-1"))
        .thenReturn(new IdentityProvider.ProvisionedIdentity("subject-1"));
    when(users.saveAndFlush(any(User.class)))
        .thenThrow(new DataIntegrityViolationException("concurrent insert"));
    when(users.findById(userId)).thenReturn(Optional.of(owned));
    when(users.findProjectedById(userId)).thenReturn(Optional.of(existing));

    assertThat(service.createProvisional(new CreateProvisionalUserInput("employee@example.com")))
        .extracting(UserResult::id, UserResult::status)
        .containsExactly(userId, UserStatus.INVITED);

    verify(identityProvider).provisionProvisionalIdentity("employee@example.com", "marker-1");
    verify(providerMutations).completeProvisionalProvision(work, "subject-1", userId);
    verify(grants).stage(any());
    verify(identityProvider, never()).deleteIdentity(any());
  }

  @Test
  void preservesMarkerOwnedIdentityWhenLocalCompletionRollsBack() {
    UserService service = service();
    IdentityProviderMutationWork work = provisionalWork();
    RuntimeException rollback = new RuntimeException("commit failed");
    when(users.findProjectedByEmail("employee@example.com")).thenReturn(Optional.empty());
    when(providerMutations.requestProvisionalProvision("employee@example.com")).thenReturn(work);
    when(identityProvider.provisionProvisionalIdentity("employee@example.com", "marker-1"))
        .thenReturn(new IdentityProvider.ProvisionedIdentity("subject-1"));
    when(transactions.execute(any(TransactionCallback.class))).thenThrow(rollback);

    assertThatThrownBy(
            () -> service.createProvisional(new CreateProvisionalUserInput("employee@example.com")))
        .isSameAs(rollback);

    verify(providerMutations)
        .recordFailure(work, rollback, IdentityProviderMutationTerminalReason.RETRY_EXHAUSTED);
    verify(identityProvider, never()).deleteIdentity(any());
  }

  @Test
  void rejectsInvitedAsOperationalUpdateStatus() {
    UUID userId = UUID.randomUUID();
    UserService service = service();
    when(users.findEntityByIdForUpdate(userId))
        .thenReturn(Optional.of(new User("kc-user-1", "a@b.com", "A")));

    assertThatThrownBy(
            () ->
                service.update(
                    userId, new UpdateUserInput(null, FieldUpdate.absent(), UserStatus.INVITED)))
        .isInstanceOf(ApiException.class)
        .hasMessage("Invited is not an operational user status.");

    verify(users, never()).saveAndFlush(any(User.class));
    verify(providerMutations, never()).requestEnabledState(any(), any(), anyBoolean());
  }

  @Test
  void durablyRequestsProviderDisableWithTheLocalStatusChange() {
    UUID userId = UUID.randomUUID();
    User user = new User("kc-user-1", "a@b.com", "A");
    UserProjection updated = projection(UserStatus.DISABLED);
    UserService service = service();
    when(users.findEntityByIdForUpdate(userId)).thenReturn(Optional.of(user));
    when(users.findProjectedById(userId)).thenReturn(Optional.of(updated));

    assertThat(
            service.update(
                userId, new UpdateUserInput(null, FieldUpdate.absent(), UserStatus.DISABLED)))
        .extracting(result -> result.id(), result -> result.status())
        .containsExactly(updated.getId(), UserStatus.DISABLED);

    InOrder order = inOrder(users, providerMutations);
    order.verify(users).saveAndFlush(user);
    order.verify(providerMutations).requestEnabledState(userId, "kc-user-1", false);
  }

  @Test
  void durablyRequestsProviderEnableWithTheLocalStatusChange() {
    UUID userId = UUID.randomUUID();
    User user = new User("kc-user-1", "a@b.com", "A");
    user.changeOperationalStatus(UserStatus.DISABLED);
    UserProjection updated = projection(UserStatus.ACTIVE);
    UserService service = service();
    when(users.findEntityByIdForUpdate(userId)).thenReturn(Optional.of(user));
    when(users.findProjectedById(userId)).thenReturn(Optional.of(updated));

    assertThat(
            service.update(
                userId, new UpdateUserInput(null, FieldUpdate.absent(), UserStatus.ACTIVE)))
        .extracting(result -> result.id(), result -> result.status())
        .containsExactly(updated.getId(), UserStatus.ACTIVE);

    InOrder order = inOrder(users, providerMutations);
    order.verify(users).saveAndFlush(user);
    order.verify(providerMutations).requestEnabledState(userId, "kc-user-1", true);
  }

  @Test
  void clearsAvatarWhenTheUpdateExplicitlyProvidesNull() {
    UUID userId = UUID.randomUUID();
    User user = new User("kc-user-1", "a@b.com", "A");
    user.setAvatarUrl("https://example.com/avatar.png");
    UserProjection updated = projection(UserStatus.ACTIVE);
    UserService service = service();
    when(users.findEntityByIdForUpdate(userId)).thenReturn(Optional.of(user));
    when(users.findProjectedById(userId)).thenReturn(Optional.of(updated));

    assertThat(service.update(userId, new UpdateUserInput(null, FieldUpdate.present(null), null)))
        .extracting(result -> result.id(), result -> result.status())
        .containsExactly(updated.getId(), UserStatus.ACTIVE);

    assertThat(user.getAvatarUrl()).isNull();
    verify(users).saveAndFlush(user);
  }

  @Test
  void searchesUsersByAuthorizationSubjects() {
    UserProjection user = projection(UserStatus.ACTIVE);
    UserService service = service();
    when(users.findProjectedByKeycloakSubjectIn(List.of("subject-1", "subject-2")))
        .thenReturn(List.of(user));

    assertThat(
            service.searchByAuthorizationSubjects(
                List.of("subject-1", " ", "subject-2", "subject-1")))
        .singleElement()
        .extracting(result -> result.id())
        .isEqualTo(user.getId());
  }

  @Test
  void authorizationAndTransactionsLiveOnServiceMethods() throws Exception {
    Method update = UserService.class.getMethod("update", UUID.class, UpdateUserInput.class);
    Method updateCurrent =
        UserService.class.getMethod("updateCurrent", String.class, UpdateCurrentUserInput.class);
    Method createProvisional =
        UserService.class.getMethod("createProvisional", CreateProvisionalUserInput.class);
    Method recoverProvisional =
        UserService.class.getMethod(
            "recoverProvisionalProvision", IdentityProviderMutationWork.class, String.class);

    assertThat(update.isAnnotationPresent(Transactional.class)).isTrue();
    assertThat(update.isAnnotationPresent(PreAuthorize.class)).isTrue();
    assertThat(updateCurrent.isAnnotationPresent(Transactional.class)).isTrue();
    assertThat(updateCurrent.isAnnotationPresent(PreAuthorize.class)).isTrue();
    assertThat(createProvisional.isAnnotationPresent(Transactional.class)).isFalse();
    assertThat(createProvisional.getAnnotation(PreAuthorize.class).value())
        .contains(IdentityPermissions.USER_PROVISION_AUTHORITY);
    assertThat(recoverProvisional.isAnnotationPresent(Transactional.class)).isTrue();
  }

  private UserService service() {
    lenient()
        .when(transactions.execute(any(TransactionCallback.class)))
        .thenAnswer(
            invocation -> invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
    return new UserService(
        users,
        new UserApplicationMapperImpl(),
        identityProvider,
        providerMutations,
        grants,
        new IdentityGrantPlanner(),
        transactions);
  }

  private PasswordProvisioningIntent intent() {
    return new PasswordProvisioningIntent(
        UUID.randomUUID(), UUID.randomUUID(), "owner@example.com", "Owner", "correlation-1");
  }

  private IdentityProviderMutationWork provisionalWork() {
    return new IdentityProviderMutationWork(
        UUID.randomUUID(),
        UUID.randomUUID(),
        IdentityProviderMutationType.PROVISION_PROVISIONAL_USER,
        null,
        null,
        "employee@example.com",
        null,
        "marker-1",
        null,
        0);
  }

  private User persisted(InvocationOnMock invocation, UUID id) {
    User user = invocation.getArgument(0);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }

  private UserProjection projection(UserStatus status) {
    return projection(UUID.randomUUID(), "kc-user-1", "employee@example.com", status);
  }

  private UserProjection projection(
      UUID id, String keycloakSubject, String email, UserStatus status) {
    return new TestUserProjection(id, keycloakSubject, email, status);
  }

  private record TestUserProjection(
      UUID id, String keycloakSubject, String email, UserStatus status) implements UserProjection {

    @Override
    public UUID getId() {
      return id;
    }

    @Override
    public String getKeycloakSubject() {
      return keycloakSubject;
    }

    @Override
    public String getEmail() {
      return email;
    }

    @Override
    public String getName() {
      return "Employee";
    }

    @Override
    public String getAvatarUrl() {
      return null;
    }

    @Override
    public UserStatus getStatus() {
      return status;
    }

    @Override
    public boolean isEmailVerified() {
      return true;
    }

    @Override
    public OffsetDateTime getCreatedAt() {
      return OffsetDateTime.now();
    }

    @Override
    public OffsetDateTime getUpdatedAt() {
      return OffsetDateTime.now();
    }
  }
}
