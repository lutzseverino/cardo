package com.odonta.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.odonta.authorization.grant.Grants;
import com.odonta.common.api.ApiException;
import com.odonta.identity.IdentityPermissions;
import com.odonta.identity.authorization.IdentityGrantPlanner;
import com.odonta.identity.model.CompleteProvisionalUserCommand;
import com.odonta.identity.model.CreateProvisionalUserCommand;
import com.odonta.identity.model.CreateUserCommand;
import com.odonta.identity.model.UpdateUserCommand;
import com.odonta.identity.model.User;
import com.odonta.identity.model.UserProjection;
import com.odonta.identity.model.UserStatus;
import com.odonta.identity.provider.IdentityProvider;
import com.odonta.identity.repository.UserRepository;
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

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserRepository users;
  @Mock private IdentityProvider identityProvider;
  @Mock private Grants grants;

  @Test
  void deletesProvisionedIdentityWhenLocalCreateFails() {
    UserService service = service();
    when(users.findProjectedByEmail("owner@example.com")).thenReturn(Optional.empty());
    when(identityProvider.provisionPasswordIdentity("owner@example.com", "password-1", "Owner"))
        .thenReturn(new IdentityProvider.ProvisionedIdentity("kc-user-1"));
    when(users.saveAndFlush(any(User.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate"));

    assertThatThrownBy(
            () -> service.create(new CreateUserCommand("owner@example.com", "password-1", "Owner")))
        .isInstanceOf(ApiException.class)
        .hasMessage("A user with this email already exists.");

    verify(identityProvider).deleteIdentity("kc-user-1");
  }

  @Test
  void deletesProvisionedIdentityWhenBindingFails() {
    UserService service = service();
    when(users.findProjectedByEmail("owner@example.com")).thenReturn(Optional.empty());
    when(identityProvider.provisionPasswordIdentity("owner@example.com", "password-1", "Owner"))
        .thenReturn(new IdentityProvider.ProvisionedIdentity("kc-user-1"));
    UUID userId = UUID.randomUUID();
    when(users.saveAndFlush(any(User.class)))
        .thenAnswer(invocation -> persisted(invocation, userId));
    RuntimeException failure = new RuntimeException("bind failed");

    doThrow(failure).when(identityProvider).bindUserId("kc-user-1", userId);

    assertThatThrownBy(
            () -> service.create(new CreateUserCommand("owner@example.com", "password-1", "Owner")))
        .isSameAs(failure);

    verify(identityProvider).deleteIdentity("kc-user-1");
  }

  @Test
  void reusesExistingProvisionalUser() {
    UserProjection existing = projection(UserStatus.INVITED);
    UserService service = service();
    when(users.findProjectedByEmail("employee@example.com")).thenReturn(Optional.of(existing));

    assertThat(service.createProvisional(new CreateProvisionalUserCommand("employee@example.com")))
        .isSameAs(existing);

    verify(identityProvider, never()).provisionProvisionalIdentity(any());
  }

  @Test
  void rejectsInvitedAsOperationalUpdateStatus() {
    UUID userId = UUID.randomUUID();
    UserService service = service();
    when(users.findById(userId)).thenReturn(Optional.of(new User("kc-user-1", "a@b.com", "A")));

    assertThatThrownBy(
            () -> service.update(userId, new UpdateUserCommand(null, null, UserStatus.INVITED)))
        .isInstanceOf(ApiException.class)
        .hasMessage("Invited is not an operational user status.");

    verify(users, never()).saveAndFlush(any(User.class));
  }

  @Test
  void flushesCompletionBeforeCompletingProviderIdentity() {
    UUID userId = UUID.randomUUID();
    User user = User.invited("kc-user-1", "employee@example.com");
    UserProjection completed = projection(UserStatus.ACTIVE);
    UserService service = service();
    ReflectionTestUtils.setField(user, "id", userId);
    when(users.findById(userId)).thenReturn(Optional.of(user));
    when(users.saveAndFlush(user)).thenReturn(user);
    when(users.findProjectedById(userId)).thenReturn(Optional.of(completed));

    assertThat(
            service.completeProvisional(
                userId, new CompleteProvisionalUserCommand("Employee", "password-1")))
        .isSameAs(completed);

    InOrder order = inOrder(users, identityProvider);
    order.verify(users).saveAndFlush(user);
    order.verify(identityProvider).completePasswordIdentity("kc-user-1", "password-1", "Employee");
  }

  @Test
  void cancelsOnlyProvisionalUsers() {
    UUID userId = UUID.randomUUID();
    User user = User.invited("kc-user-1", "employee@example.com");
    UserService service = service();
    when(users.findById(userId)).thenReturn(Optional.of(user));

    service.cancelProvisional(userId);

    InOrder order = inOrder(users, identityProvider);
    order.verify(users).delete(user);
    order.verify(users).flush();
    order.verify(identityProvider).deleteIdentity("kc-user-1");
  }

  @Test
  void rejectsCancellingCompletedUsers() {
    UUID userId = UUID.randomUUID();
    UserService service = service();
    when(users.findById(userId)).thenReturn(Optional.of(new User("kc-user-1", "a@b.com", "A")));

    assertThatThrownBy(() -> service.cancelProvisional(userId))
        .isInstanceOf(ApiException.class)
        .hasMessage("User is already complete.");

    verify(identityProvider, never()).deleteIdentity(any());
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
        .containsExactly(user);
  }

  @Test
  void authorizationAndTransactionsLiveOnServiceMethods() throws Exception {
    Method update = UserService.class.getMethod("update", UUID.class, UpdateUserCommand.class);
    Method updateCurrent =
        UserService.class.getMethod(
            "updateCurrent",
            String.class,
            com.odonta.identity.model.UpdateCurrentUserCommand.class);
    Method createProvisional =
        UserService.class.getMethod(
            "createProvisional", com.odonta.identity.model.CreateProvisionalUserCommand.class);

    assertThat(update.isAnnotationPresent(Transactional.class)).isTrue();
    assertThat(update.isAnnotationPresent(PreAuthorize.class)).isTrue();
    assertThat(updateCurrent.isAnnotationPresent(Transactional.class)).isTrue();
    assertThat(updateCurrent.isAnnotationPresent(PreAuthorize.class)).isTrue();
    assertThat(createProvisional.getAnnotation(PreAuthorize.class).value())
        .contains(IdentityPermissions.USER_PROVISION_AUTHORITY);
  }

  private UserService service() {
    return new UserService(users, identityProvider, grants, new IdentityGrantPlanner());
  }

  private User persisted(InvocationOnMock invocation, UUID id) {
    User user = invocation.getArgument(0);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }

  private UserProjection projection(UserStatus status) {
    return new TestUserProjection(UUID.randomUUID(), status);
  }

  private record TestUserProjection(UUID id, UserStatus status) implements UserProjection {

    @Override
    public UUID getId() {
      return id;
    }

    @Override
    public String getKeycloakSubject() {
      return "kc-user-1";
    }

    @Override
    public String getEmail() {
      return "employee@example.com";
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
