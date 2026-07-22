package io.github.lutzseverino.cardo.integration.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.authorization.spring.AuthenticatedUser;
import io.github.lutzseverino.cardo.identity.client.IdentityUser;
import io.github.lutzseverino.cardo.identity.client.IdentityUsersClient;
import io.github.lutzseverino.cardo.invite.client.CreatedInvitation;
import io.github.lutzseverino.cardo.invite.client.Invitation;
import io.github.lutzseverino.cardo.invite.client.InvitationsClient;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReferenceWorkflowTest {

  private static final UUID INVITATION = UUID.fromString("34000000-0000-0000-0000-000000000010");
  private static final UUID REMOTE = UUID.fromString("34000000-0000-0000-0000-000000000011");
  private static final UUID OWNER = UUID.fromString("34000000-0000-0000-0000-000000000012");
  private static final UUID INVITED_USER = UUID.fromString("34000000-0000-0000-0000-000000000013");
  private static final OffsetDateTime ACCEPTED_AT =
      OffsetDateTime.of(2026, 7, 22, 12, 0, 0, 0, ZoneOffset.UTC);

  @Test
  void dispatchesDurableCreateThroughTheStableInviteClientWithoutRetainingToken() {
    ReferenceProductStore store = mock(ReferenceProductStore.class);
    InvitationsClient invitations = mock(InvitationsClient.class);
    IdentityUsersClient identity = mock(IdentityUsersClient.class);
    ReferenceAcceptanceCommitter committer = mock(ReferenceAcceptanceCommitter.class);
    ReferenceProductStore.ReferenceCommand command =
        new ReferenceProductStore.ReferenceCommand(
            UUID.randomUUID(), ReferenceProductStore.CommandType.CREATE, INVITATION, null, null);
    when(store.invitation(INVITATION))
        .thenReturn(
            new ReferenceProductStore.InvitationState(
                INVITATION, INVITATION, "invited@example.test", OWNER, null, null, null, null));
    CreatedInvitation created = mock(CreatedInvitation.class);
    Invitation remote = mock(Invitation.class);
    when(remote.id()).thenReturn(REMOTE);
    when(remote.invitedUserId()).thenReturn(INVITED_USER);
    when(created.invitation()).thenReturn(remote);
    when(created.acceptUrl()).thenReturn(URI.create("https://secret.example/token"));
    when(invitations.create(any())).thenReturn(created);

    ReferenceWorkflow workflow = workflow(store, invitations, identity, committer);
    workflow.dispatch(command);

    ArgumentCaptor<io.github.lutzseverino.cardo.invite.client.CreateInvitation> request =
        ArgumentCaptor.forClass(io.github.lutzseverino.cardo.invite.client.CreateInvitation.class);
    verify(invitations).create(request.capture());
    assertThat(request.getValue().requestId()).isEqualTo(INVITATION);
    assertThat(request.getValue().tenantId()).isEqualTo(ReferenceContract.TENANT_ID);
    verify(store).recordCreated(INVITATION, REMOTE, INVITED_USER);
    verify(store).completeCommand(command.id());
  }

  @Test
  void retriesAfterRemoteSuccessAndCommitsLocallyExactlyOnce() {
    ReferenceProductStore store = mock(ReferenceProductStore.class);
    InvitationsClient invitations = mock(InvitationsClient.class);
    IdentityUsersClient identity = mock(IdentityUsersClient.class);
    ReferenceAcceptanceCommitter committer = mock(ReferenceAcceptanceCommitter.class);
    ReferenceProductStore.ReferenceCommand command =
        new ReferenceProductStore.ReferenceCommand(
            UUID.randomUUID(),
            ReferenceProductStore.CommandType.ACCEPT,
            INVITATION,
            "provider-subject",
            ACCEPTED_AT);
    when(store.invitation(INVITATION))
        .thenReturn(
            new ReferenceProductStore.InvitationState(
                INVITATION,
                INVITATION,
                "invited@example.test",
                OWNER,
                REMOTE,
                INVITED_USER,
                "provider-subject",
                null));

    ReferenceWorkflow workflow = workflow(store, invitations, identity, committer);
    workflow.failNextAfterRemoteAccept();
    assertThatThrownBy(() -> workflow.dispatch(command))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Controlled remote-success/local-gap failure.");
    workflow.dispatch(command);

    verify(invitations, times(2)).accept(REMOTE, ACCEPTED_AT);
    verify(committer).complete(command);
  }

  @Test
  void rejectsAcceptanceByAUserOtherThanTheImmutableInvitee() {
    ReferenceProductStore store = mock(ReferenceProductStore.class);
    InvitationsClient invitations = mock(InvitationsClient.class);
    IdentityUsersClient identity = mock(IdentityUsersClient.class);
    ReferenceAcceptanceCommitter committer = mock(ReferenceAcceptanceCommitter.class);
    when(store.invitation(INVITATION)).thenReturn(invitation(null));
    Invitation remote = mock(Invitation.class);
    when(remote.invitedUserId()).thenReturn(INVITED_USER);
    when(invitations.get(REMOTE)).thenReturn(remote);
    IdentityUser invited = mock(IdentityUser.class);
    when(invited.id()).thenReturn(INVITED_USER);
    when(invited.authorizationSubject()).thenReturn("invited-subject");
    when(identity.get(INVITED_USER)).thenReturn(invited);
    ReferenceWorkflow workflow = workflow(store, invitations, identity, committer);

    assertThatThrownBy(
            () ->
                workflow.accept(
                    INVITATION,
                    new AuthenticatedUser(UUID.randomUUID(), "other-subject", "Other"),
                    ACCEPTED_AT))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    verify(store, never()).recordAcceptanceIntent(any(), any(), any());
  }

  @Test
  void bindsConvergenceToTheAcceptedSubjectWithoutRepeatingRemoteLookups() {
    ReferenceProductStore store = mock(ReferenceProductStore.class);
    InvitationsClient invitations = mock(InvitationsClient.class);
    IdentityUsersClient identity = mock(IdentityUsersClient.class);
    ReferenceAcceptanceCommitter committer = mock(ReferenceAcceptanceCommitter.class);
    when(store.invitation(INVITATION)).thenReturn(invitation("invited-subject"));
    ReferenceWorkflow workflow = workflow(store, invitations, identity, committer);

    assertThat(
            workflow.requireOwnedInvitation(
                INVITATION, new AuthenticatedUser(INVITED_USER, "invited-subject", "Invited")))
        .isEqualTo(invitation("invited-subject"));
    assertThatThrownBy(
            () ->
                workflow.requireOwnedInvitation(
                    INVITATION, new AuthenticatedUser(UUID.randomUUID(), "other-subject", "Other")))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    verifyNoInteractions(invitations, identity);
  }

  private ReferenceProductStore.InvitationState invitation(String acceptedSubject) {
    return new ReferenceProductStore.InvitationState(
        INVITATION,
        INVITATION,
        "invited@example.test",
        OWNER,
        REMOTE,
        INVITED_USER,
        acceptedSubject,
        null);
  }

  private ReferenceWorkflow workflow(
      ReferenceProductStore store,
      InvitationsClient invitations,
      IdentityUsersClient identity,
      ReferenceAcceptanceCommitter committer) {
    return new ReferenceWorkflow(
        store,
        invitations,
        identity,
        committer,
        URI.create("https://reference.example/invitations/accept"));
  }
}
