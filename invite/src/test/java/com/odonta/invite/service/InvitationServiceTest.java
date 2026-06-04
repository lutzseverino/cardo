package com.odonta.invite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.odonta.authorization.access.AccessProfileProjection;
import com.odonta.authorization.access.AccessProfileService;
import com.odonta.authorization.spring.AuthenticatedUser;
import com.odonta.authorization.sync.AuthorizationSyncService;
import com.odonta.identity.client.UserResponse;
import com.odonta.identity.client.UserStatus;
import com.odonta.identity.client.api.UsersApi;
import com.odonta.invite.config.InvitationProperties;
import com.odonta.invite.model.CreateInvitationCommand;
import com.odonta.invite.model.Invitation;
import com.odonta.invite.repository.InvitationRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID ACCESS_PROFILE_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID INVITED_USER_ID =
      UUID.fromString("33333333-3333-3333-3333-333333333333");

  @Mock private AccessProfileService accessProfiles;
  @Mock private AuthorizationSyncService authorizationSync;
  @Mock private EmailSender email;
  @Mock private UsersApi identityUsers;
  @Mock private InvitationRepository invitations;

  @Test
  void cancelsProvisionalUserWhenInvitationPersistenceFails() {
    InvitationService service = service();
    RuntimeException failure = new RuntimeException("database unavailable");
    when(accessProfiles.availableProfile(ACCESS_PROFILE_ID, "clinic", TENANT_ID))
        .thenReturn(Optional.of(accessProfile()));
    when(identityUsers.createProvisionalUser(any())).thenReturn(identityUser());
    when(invitations.saveAndFlush(any(Invitation.class))).thenThrow(failure);

    assertThatThrownBy(() -> service.create(inviter(), command())).isSameAs(failure);

    verify(identityUsers).cancelProvisionalUser(INVITED_USER_ID);
  }

  @Test
  void keepsOriginalFailureWhenCompensationFails() {
    InvitationService service = service();
    RuntimeException failure = new RuntimeException("database unavailable");
    RuntimeException compensationFailure = new RuntimeException("identity unavailable");
    when(accessProfiles.availableProfile(ACCESS_PROFILE_ID, "clinic", TENANT_ID))
        .thenReturn(Optional.of(accessProfile()));
    when(identityUsers.createProvisionalUser(any())).thenReturn(identityUser());
    when(invitations.saveAndFlush(any(Invitation.class))).thenThrow(failure);
    doThrow(compensationFailure).when(identityUsers).cancelProvisionalUser(INVITED_USER_ID);

    assertThatThrownBy(() -> service.create(inviter(), command()))
        .isSameAs(failure)
        .satisfies(
            exception ->
                assertThat(exception.getSuppressed()).containsExactly(compensationFailure));
  }

  private InvitationService service() {
    return new InvitationService(
        accessProfiles,
        authorizationSync,
        email,
        identityUsers,
        new InvitationProperties(Duration.ofHours(72), "https://app.example.com"),
        invitations);
  }

  private AuthenticatedUser inviter() {
    return new AuthenticatedUser(
        UUID.fromString("44444444-4444-4444-4444-444444444444"), "owner-subject", "Owner");
  }

  private CreateInvitationCommand command() {
    return new CreateInvitationCommand(
        TENANT_ID, "clinic:clinic", "employee@example.com", ACCESS_PROFILE_ID);
  }

  private UserResponse identityUser() {
    return new UserResponse()
        .id(INVITED_USER_ID)
        .authorizationSubject("employee-subject")
        .email("employee@example.com")
        .status(UserStatus.INVITED)
        .emailVerified(false)
        .createdAt(OffsetDateTime.now())
        .updatedAt(OffsetDateTime.now());
  }

  private AccessProfileProjection accessProfile() {
    return new TestAccessProfileProjection();
  }

  private record TestAccessProfileProjection() implements AccessProfileProjection {

    @Override
    public UUID getId() {
      return ACCESS_PROFILE_ID;
    }

    @Override
    public String getProduct() {
      return "clinic";
    }

    @Override
    public UUID getTenantId() {
      return TENANT_ID;
    }

    @Override
    public String getName() {
      return "Employee";
    }

    @Override
    public String getDescription() {
      return null;
    }

    @Override
    public boolean isTemplate() {
      return false;
    }
  }
}
