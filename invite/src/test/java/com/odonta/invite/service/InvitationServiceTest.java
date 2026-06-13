package com.odonta.invite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.odonta.authorization.access.AccessGrant;
import com.odonta.authorization.access.AccessProfileProjection;
import com.odonta.authorization.access.AccessProfileService;
import com.odonta.authorization.grant.GrantPlan;
import com.odonta.authorization.grant.Grants;
import com.odonta.authorization.spring.AuthenticatedUser;
import com.odonta.identity.client.IdentityUsersClient;
import com.odonta.identity.client.ProvisionalUser;
import com.odonta.invite.api.model.CreateInvitationInput;
import com.odonta.invite.authorization.InvitationGrantPlanner;
import com.odonta.invite.config.InvitationProperties;
import com.odonta.invite.model.Invitation;
import com.odonta.invite.model.InvitationProjection;
import com.odonta.invite.model.InvitationStatus;
import com.odonta.invite.repository.InvitationRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
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
  @Mock private EmailSender email;
  @Mock private Grants grants;
  @Mock private IdentityUsersClient identityUsers;
  @Mock private InvitationGrantPlanner invitationGrantPlanner;
  @Mock private InvitationRepository invitations;

  @Test
  void cancelsProvisionalUserWhenInvitationPersistenceFails() {
    InvitationService service = service();
    RuntimeException failure = new RuntimeException("database unavailable");
    when(accessProfiles.availableProfile(ACCESS_PROFILE_ID, "clinic", TENANT_ID))
        .thenReturn(Optional.of(accessProfile()));
    when(identityUsers.createProvisional(any())).thenReturn(identityUser());
    when(invitations.saveAndFlush(any(Invitation.class))).thenThrow(failure);

    assertThatThrownBy(() -> service.create(inviter(), input())).isSameAs(failure);

    verify(identityUsers).cancelProvisional(INVITED_USER_ID);
  }

  @Test
  void keepsOriginalFailureWhenCompensationFails() {
    InvitationService service = service();
    RuntimeException failure = new RuntimeException("database unavailable");
    RuntimeException compensationFailure = new RuntimeException("identity unavailable");
    when(accessProfiles.availableProfile(ACCESS_PROFILE_ID, "clinic", TENANT_ID))
        .thenReturn(Optional.of(accessProfile()));
    when(identityUsers.createProvisional(any())).thenReturn(identityUser());
    when(invitations.saveAndFlush(any(Invitation.class))).thenThrow(failure);
    doThrow(compensationFailure).when(identityUsers).cancelProvisional(INVITED_USER_ID);

    assertThatThrownBy(() -> service.create(inviter(), input()))
        .isSameAs(failure)
        .satisfies(
            exception ->
                assertThat(exception.getSuppressed()).containsExactly(compensationFailure));
  }

  @Test
  void resolvesProfileGrantsBeforePlanningAcceptance() {
    InvitationService service = service();
    InvitationProjection invitation = mock(InvitationProjection.class);
    Invitation entity = mock(Invitation.class);
    GrantPlan plan = mock(GrantPlan.class);
    List<AccessGrant> accessGrants = List.of(new AccessGrant("clinic:clinic", null, "read"));
    when(invitation.getId()).thenReturn(UUID.randomUUID());
    when(invitation.getTenantId()).thenReturn(TENANT_ID);
    when(invitation.getTenantResourceType()).thenReturn("clinic:clinic");
    when(invitation.getAccessProfileId()).thenReturn(ACCESS_PROFILE_ID);
    when(invitation.getInvitedUserId()).thenReturn(INVITED_USER_ID);
    when(invitation.getStatus()).thenReturn(InvitationStatus.PENDING);
    when(invitation.getCreatedAt()).thenReturn(OffsetDateTime.now());
    when(invitations.findProjectedByToken("token")).thenReturn(Optional.of(invitation));
    when(invitations.findById(invitation.getId())).thenReturn(Optional.of(entity));
    when(accessProfiles.grants(ACCESS_PROFILE_ID)).thenReturn(accessGrants);
    when(invitationGrantPlanner.acceptance(
            TENANT_ID, "clinic:clinic", "employee-subject", accessGrants))
        .thenReturn(plan);

    service.accept("token", new AuthenticatedUser(INVITED_USER_ID, "employee-subject", "Employee"));

    verify(invitationGrantPlanner)
        .acceptance(TENANT_ID, "clinic:clinic", "employee-subject", accessGrants);
    verify(grants).stage(plan);
  }

  private InvitationService service() {
    return new InvitationService(
        accessProfiles,
        email,
        grants,
        identityUsers,
        invitationGrantPlanner,
        new InvitationProperties(Duration.ofHours(72), "https://app.example.com"),
        invitations);
  }

  private AuthenticatedUser inviter() {
    return new AuthenticatedUser(
        UUID.fromString("44444444-4444-4444-4444-444444444444"), "owner-subject", "Owner");
  }

  private CreateInvitationInput input() {
    return new CreateInvitationInput(
        TENANT_ID, "clinic:clinic", "employee@example.com", ACCESS_PROFILE_ID);
  }

  private ProvisionalUser identityUser() {
    return new ProvisionalUser(INVITED_USER_ID, "employee-subject");
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
