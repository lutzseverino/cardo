package io.github.lutzseverino.cardo.invite.integration.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.invite.model.InvitationStatus;
import io.github.lutzseverino.cardo.invite.provider.InvitationSender;
import io.github.lutzseverino.cardo.invite.repository.InvitationProjection;
import io.github.lutzseverino.cardo.invite.repository.InvitationRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvitationDeliveryProcessorTest {

  @Test
  void resolvesDeliveryDataAfterCommitAndSendsTheInvitation() {
    InvitationSender sender = mock(InvitationSender.class);
    InvitationRepository invitations = mock(InvitationRepository.class);
    InvitationProjection invitation = mock(InvitationProjection.class);
    UUID invitationId = UUID.randomUUID();
    when(invitations.findProjectedById(invitationId)).thenReturn(Optional.of(invitation));
    when(invitation.getStatus()).thenReturn(InvitationStatus.PENDING);
    when(invitation.getExpiresAt()).thenReturn(OffsetDateTime.now().plusDays(1));
    when(invitation.getInvitedEmail()).thenReturn("employee@example.com");
    when(invitation.getAcceptUrlBase()).thenReturn("https://clinic.example.com/invitations");
    when(invitation.getToken()).thenReturn("token-1");

    processor(sender, invitations).deliver(new InvitationDeliveryRequested(invitationId));

    verify(sender).send("employee@example.com", "https://clinic.example.com/invitations/token-1");
  }

  @Test
  void doesNotDeliverAnInvitationThatIsNoLongerPending() {
    InvitationSender sender = mock(InvitationSender.class);
    InvitationRepository invitations = mock(InvitationRepository.class);
    InvitationProjection invitation = mock(InvitationProjection.class);
    UUID invitationId = UUID.randomUUID();
    when(invitations.findProjectedById(invitationId)).thenReturn(Optional.of(invitation));
    when(invitation.getStatus()).thenReturn(InvitationStatus.REVOKED);

    processor(sender, invitations).deliver(new InvitationDeliveryRequested(invitationId));

    verifyNoInteractions(sender);
  }

  @Test
  void doesNotDeliverAnExpiredInvitationDuringRecovery() {
    InvitationSender sender = mock(InvitationSender.class);
    InvitationRepository invitations = mock(InvitationRepository.class);
    InvitationProjection invitation = mock(InvitationProjection.class);
    UUID invitationId = UUID.randomUUID();
    when(invitations.findProjectedById(invitationId)).thenReturn(Optional.of(invitation));
    when(invitation.getStatus()).thenReturn(InvitationStatus.PENDING);
    when(invitation.getExpiresAt()).thenReturn(OffsetDateTime.now().minusDays(1));

    processor(sender, invitations).deliver(new InvitationDeliveryRequested(invitationId));

    verifyNoInteractions(sender);
  }

  private InvitationDeliveryProcessor processor(
      InvitationSender sender, InvitationRepository invitations) {
    return new InvitationDeliveryProcessor(sender, invitations);
  }
}
