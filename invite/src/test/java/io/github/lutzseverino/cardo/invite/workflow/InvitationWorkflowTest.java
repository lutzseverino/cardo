package io.github.lutzseverino.cardo.invite.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.authorization.grant.GrantPlan;
import io.github.lutzseverino.cardo.authorization.grant.Grants;
import io.github.lutzseverino.cardo.identity.client.IdentityUsersClient;
import io.github.lutzseverino.cardo.identity.client.ProvisionalUser;
import io.github.lutzseverino.cardo.invite.authorization.InvitationGrantPlanner;
import io.github.lutzseverino.cardo.invite.model.CreateInvitationInput;
import io.github.lutzseverino.cardo.invite.model.CreateInvitationResult;
import io.github.lutzseverino.cardo.invite.model.InvitationGrantInput;
import io.github.lutzseverino.cardo.invite.model.InvitationResult;
import io.github.lutzseverino.cardo.invite.model.InvitationStatus;
import io.github.lutzseverino.cardo.invite.model.PendingInvitation;
import io.github.lutzseverino.cardo.invite.service.InvitationService;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvitationWorkflowTest {

  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID INVITATION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID INVITED_USER_ID =
      UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID INVITER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

  @Test
  void keepsTheReusableProvisionalUserWhenInvitationCreationFails() {
    IdentityUsersClient identityUsers = mock(IdentityUsersClient.class);
    InvitationService invitations = mock(InvitationService.class);
    CreateInvitationWorkflow workflow = new CreateInvitationWorkflow(identityUsers, invitations);
    RuntimeException failure = new RuntimeException("database unavailable");
    when(invitations.findCreated("clinic", input())).thenReturn(Optional.empty());
    when(identityUsers.createProvisional(input().email())).thenReturn(identityUser());
    when(invitations.create("clinic", input(), INVITED_USER_ID, "employee-subject"))
        .thenThrow(failure);

    assertThatThrownBy(() -> workflow.create("clinic", input())).isSameAs(failure);

    verify(identityUsers, never()).cancelProvisional(INVITED_USER_ID);
  }

  @Test
  void returnsAnExistingInvitationForAnIdempotentCreateRetry() {
    IdentityUsersClient identityUsers = mock(IdentityUsersClient.class);
    InvitationService invitations = mock(InvitationService.class);
    CreateInvitationWorkflow workflow = new CreateInvitationWorkflow(identityUsers, invitations);
    CreateInvitationResult existing = mock(CreateInvitationResult.class);
    when(invitations.findCreated("clinic", input())).thenReturn(Optional.of(existing));

    assertThat(workflow.create("clinic", input())).isSameAs(existing);

    verify(identityUsers, never()).createProvisional(input().email());
    verify(invitations, never())
        .create(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void rejectsResourcesOwnedByAnotherProductBeforeCallingExternalOwners() {
    IdentityUsersClient identityUsers = mock(IdentityUsersClient.class);
    InvitationService invitations = mock(InvitationService.class);
    CreateInvitationWorkflow workflow = new CreateInvitationWorkflow(identityUsers, invitations);
    CreateInvitationInput input =
        new CreateInvitationInput(
            input().requestId(),
            TENANT_ID,
            "clinic:clinic",
            input().email(),
            "clinic:employee",
            List.of(new InvitationGrantInput("polity:polity", "read")),
            INVITER_ID,
            input().acceptUrlBase());

    assertThatThrownBy(() -> workflow.create("clinic", input))
        .hasMessageContaining("calling product");

    verifyNoInteractions(identityUsers, invitations);
  }

  @Test
  void appliesTheInvitationGrantSnapshotWhenAccepting() {
    OffsetDateTime acceptedAt = OffsetDateTime.parse("2026-07-17T10:00:00Z");
    Grants grants = mock(Grants.class);
    InvitationGrantPlanner planner = mock(InvitationGrantPlanner.class);
    InvitationService invitations = mock(InvitationService.class);
    InvitationAcceptanceApplicator applicator =
        new InvitationAcceptanceApplicator(grants, planner, invitations);
    AcceptInvitationWorkflow workflow = new AcceptInvitationWorkflow(applicator, invitations);
    PendingInvitation invitation =
        new PendingInvitation(
            INVITATION_ID,
            "clinic",
            TENANT_ID,
            "clinic:clinic",
            "clinic:employee",
            grants(),
            INVITED_USER_ID,
            "employee-subject",
            acceptedAt.plusDays(1));
    GrantPlan plan = mock(GrantPlan.class);
    when(invitations.get(INVITATION_ID, "clinic"))
        .thenReturn(invitationResult(InvitationStatus.PENDING))
        .thenReturn(invitationResult(InvitationStatus.ACCEPTED));
    when(invitations.requirePending(INVITATION_ID, "clinic", acceptedAt)).thenReturn(invitation);
    when(invitations.accept(INVITATION_ID, acceptedAt)).thenReturn(true);
    when(planner.acceptance(TENANT_ID, "employee-subject", grants())).thenReturn(plan);

    InvitationResult accepted = workflow.accept(INVITATION_ID, "clinic", acceptedAt);

    assertThat(accepted.status()).isEqualTo(InvitationStatus.ACCEPTED);
    verify(invitations).accept(INVITATION_ID, acceptedAt);
    verify(grants).stage(plan);
  }

  @Test
  void returnsAnAlreadyAcceptedInvitationWithoutApplyingGrantsAgain() {
    OffsetDateTime acceptedAt = OffsetDateTime.parse("2026-07-17T10:00:00Z");
    Grants grants = mock(Grants.class);
    InvitationGrantPlanner planner = mock(InvitationGrantPlanner.class);
    InvitationService invitations = mock(InvitationService.class);
    InvitationAcceptanceApplicator applicator =
        new InvitationAcceptanceApplicator(grants, planner, invitations);
    AcceptInvitationWorkflow workflow = new AcceptInvitationWorkflow(applicator, invitations);
    InvitationResult accepted = invitationResult(InvitationStatus.ACCEPTED);
    when(invitations.get(INVITATION_ID, "clinic")).thenReturn(accepted);

    assertThat(workflow.accept(INVITATION_ID, "clinic", acceptedAt)).isSameAs(accepted);

    verify(invitations, never()).requirePending(INVITATION_ID, "clinic", acceptedAt);
    verify(invitations, never()).accept(INVITATION_ID, acceptedAt);
    verify(grants, never()).stage(org.mockito.ArgumentMatchers.any());
  }

  private CreateInvitationInput input() {
    return new CreateInvitationInput(
        UUID.fromString("55555555-5555-5555-5555-555555555555"),
        TENANT_ID,
        "clinic:clinic",
        "employee@example.com",
        "clinic:employee",
        grants(),
        INVITER_ID,
        URI.create("https://clinic.example.com/invitations"));
  }

  private ProvisionalUser identityUser() {
    return new ProvisionalUser(INVITED_USER_ID, "employee-subject");
  }

  private List<InvitationGrantInput> grants() {
    return List.of(new InvitationGrantInput("clinic:clinic", "read"));
  }

  private InvitationResult invitationResult(InvitationStatus status) {
    OffsetDateTime now = OffsetDateTime.parse("2026-07-17T10:00:00Z");
    return new InvitationResult(
        INVITATION_ID,
        input().requestId(),
        TENANT_ID,
        "clinic:clinic",
        "clinic:employee",
        input().email(),
        INVITED_USER_ID,
        INVITER_ID,
        status,
        now.plusDays(3),
        status == InvitationStatus.ACCEPTED ? now : null,
        null,
        now,
        now);
  }
}
