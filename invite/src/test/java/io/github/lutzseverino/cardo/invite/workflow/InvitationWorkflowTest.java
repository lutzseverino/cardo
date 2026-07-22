package io.github.lutzseverino.cardo.invite.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.identity.client.IdentityUsersClient;
import io.github.lutzseverino.cardo.invite.model.CreateInvitationInput;
import io.github.lutzseverino.cardo.invite.model.CreateInvitationResult;
import io.github.lutzseverino.cardo.invite.model.InvitationResult;
import io.github.lutzseverino.cardo.invite.model.InvitationStatus;
import io.github.lutzseverino.cardo.invite.service.InvitationCompletionService;
import io.github.lutzseverino.cardo.invite.service.InvitationService;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvitationWorkflowTest {

  private static final UUID INVITATION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID INVITED_USER_ID =
      UUID.fromString("33333333-3333-3333-3333-333333333333");

  @Test
  void returnsAnExistingInvitationWithoutProvisioningAnotherIdentity() {
    IdentityUsersClient identityUsers = mock(IdentityUsersClient.class);
    InvitationService invitations = mock(InvitationService.class);
    CreateInvitationResult existing = mock(CreateInvitationResult.class);
    when(invitations.findCreated("clinic", input())).thenReturn(Optional.of(existing));

    assertThat(new CreateInvitationWorkflow(identityUsers, invitations).create("clinic", input()))
        .isSameAs(existing);

    verify(identityUsers, never()).createProvisional(input().email());
  }

  @Test
  void rejectsAnotherProductsResourceBeforeCrossingAnOwnerBoundary() {
    IdentityUsersClient identityUsers = mock(IdentityUsersClient.class);
    InvitationService invitations = mock(InvitationService.class);
    CreateInvitationInput foreign =
        new CreateInvitationInput(
            input().requestId(),
            input().tenantId(),
            "polity:polity",
            input().email(),
            input().invitedBy(),
            input().acceptUrlBase());

    assertThatThrownBy(
            () ->
                new CreateInvitationWorkflow(identityUsers, invitations).create("clinic", foreign))
        .hasMessageContaining("calling product");

    verifyNoInteractions(identityUsers, invitations);
  }

  @Test
  void acceptanceOnlyTransitionsInviteLifecycleAndReturnsItsProjection() {
    InvitationService invitations = mock(InvitationService.class);
    OffsetDateTime acceptedAt = OffsetDateTime.parse("2026-07-17T10:00:00Z");
    InvitationResult accepted = result(InvitationStatus.ACCEPTED);
    when(invitations.get(INVITATION_ID, "clinic")).thenReturn(accepted);

    assertThat(
            new AcceptInvitationWorkflow(invitations).accept(INVITATION_ID, "clinic", acceptedAt))
        .isSameAs(accepted);

    org.mockito.InOrder order = org.mockito.Mockito.inOrder(invitations);
    order.verify(invitations).accept(INVITATION_ID, "clinic", acceptedAt);
    order.verify(invitations).get(INVITATION_ID, "clinic");
  }

  @Test
  void revocationTerminalizesCompletionInsideTheInviteLifecycleTransaction() {
    InvitationCompletionService completions = mock(InvitationCompletionService.class);
    InvitationService invitations = mock(InvitationService.class);
    InvitationResult revoked = result(InvitationStatus.REVOKED);
    when(invitations.get(INVITATION_ID, "clinic")).thenReturn(revoked);

    assertThat(
            new RevokeInvitationWorkflow(completions, invitations).revoke(INVITATION_ID, "clinic"))
        .isSameAs(revoked);

    org.mockito.InOrder order = org.mockito.Mockito.inOrder(invitations, completions);
    order.verify(invitations).revoke(INVITATION_ID, "clinic");
    order.verify(completions).revoke(INVITATION_ID);
    order.verify(invitations).get(INVITATION_ID, "clinic");
  }

  private CreateInvitationInput input() {
    return new CreateInvitationInput(
        UUID.fromString("55555555-5555-5555-5555-555555555555"),
        UUID.fromString("11111111-1111-1111-1111-111111111111"),
        "clinic:clinic",
        "employee@example.com",
        UUID.fromString("44444444-4444-4444-4444-444444444444"),
        URI.create("https://clinic.example.com/invitations"));
  }

  private InvitationResult result(InvitationStatus status) {
    OffsetDateTime now = OffsetDateTime.parse("2026-07-17T10:00:00Z");
    return new InvitationResult(
        INVITATION_ID,
        input().requestId(),
        input().tenantId(),
        input().tenantResourceType(),
        input().email(),
        INVITED_USER_ID,
        input().invitedBy(),
        status,
        now.plusDays(3),
        status == InvitationStatus.ACCEPTED ? now : null,
        status == InvitationStatus.REVOKED ? now : null,
        now,
        now);
  }
}
