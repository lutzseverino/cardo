package io.github.lutzseverino.cardo.integration.reference;

import io.github.lutzseverino.cardo.invite.client.CreateInvitation;
import io.github.lutzseverino.cardo.invite.client.InvitationCompletion;
import io.github.lutzseverino.cardo.invite.client.InvitationToken;
import io.github.lutzseverino.cardo.invite.client.InvitationsClient;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
class ReferenceWorkflow {

  private final ReferenceProductStore store;
  private final InvitationsClient invitations;
  private final ReferenceAcceptanceCommitter acceptanceCommitter;
  private final URI acceptUrl;
  private final AtomicBoolean failAfterRemoteAccept = new AtomicBoolean();

  ReferenceWorkflow(
      ReferenceProductStore store,
      InvitationsClient invitations,
      ReferenceAcceptanceCommitter acceptanceCommitter,
      @Value("${reference.accept-url}") URI acceptUrl) {
    this.store = store;
    this.invitations = invitations;
    this.acceptanceCommitter = acceptanceCommitter;
    this.acceptUrl = acceptUrl;
  }

  ReferenceProductStore.InvitationState create(UUID requestId, String email, UUID invitedBy) {
    return store.createInvitation(requestId, email, invitedBy);
  }

  void accept(UUID invitationId, String subject, OffsetDateTime acceptedAt) {
    store.recordAcceptanceIntent(invitationId, subject, acceptedAt);
  }

  void failNextAfterRemoteAccept() {
    failAfterRemoteAccept.set(true);
  }

  InvitationToken resolve(String token) {
    return invitations.getByToken(token);
  }

  InvitationCompletion requestCredentialSetup(String token) {
    return invitations.requestCompletion(token);
  }

  InvitationCompletion credentialSetup(String token) {
    return invitations.getCompletion(token);
  }

  @Scheduled(fixedDelayString = "${reference.commands.dispatch-delay:PT0.2S}")
  void dispatch() {
    store.nextCommand().ifPresent(this::dispatch);
  }

  void dispatch(ReferenceProductStore.ReferenceCommand command) {
    switch (command.type()) {
      case CREATE -> createRemotely(command);
      case ACCEPT -> acceptRemotely(command);
    }
  }

  private void createRemotely(ReferenceProductStore.ReferenceCommand command) {
    ReferenceProductStore.InvitationState state = store.invitation(command.invitationId());
    var created =
        invitations.create(
            new CreateInvitation(
                state.requestId(),
                ReferenceContract.TENANT_ID,
                "reference:tenant",
                state.email(),
                state.invitedBy(),
                acceptUrl));
    store.recordCreated(command.invitationId(), created.invitation().id());
    store.completeCommand(command.id());
  }

  private void acceptRemotely(ReferenceProductStore.ReferenceCommand command) {
    ReferenceProductStore.InvitationState state = store.invitation(command.invitationId());
    invitations.accept(state.remoteInvitationId(), command.acceptedAt());
    if (failAfterRemoteAccept.compareAndSet(true, false)) {
      throw new IllegalStateException("Controlled remote-success/local-gap failure.");
    }
    acceptanceCommitter.complete(command);
  }
}
