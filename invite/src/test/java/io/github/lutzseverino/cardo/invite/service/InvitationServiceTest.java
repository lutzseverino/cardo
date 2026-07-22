package io.github.lutzseverino.cardo.invite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.invite.config.InvitationProperties;
import io.github.lutzseverino.cardo.invite.mapper.InvitationApplicationMapper;
import io.github.lutzseverino.cardo.invite.model.CreateInvitationInput;
import io.github.lutzseverino.cardo.invite.model.Invitation;
import io.github.lutzseverino.cardo.invite.provider.InvitationDelivery;
import io.github.lutzseverino.cardo.invite.repository.InvitationProjection;
import io.github.lutzseverino.cardo.invite.repository.InvitationRepository;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvitationServiceTest {

  @Test
  void acceptsThroughTheLockedInvitationAndReportsTheSingleStateTransition() {
    InvitationRepository invitations = mock(InvitationRepository.class);
    Invitation invitation = mock(Invitation.class);
    UUID invitationId = UUID.randomUUID();
    OffsetDateTime acceptedAt = OffsetDateTime.now().minusMinutes(1);
    when(invitations.findEntityByIdForUpdate(invitationId)).thenReturn(Optional.of(invitation));
    when(invitation.getProduct()).thenReturn("clinic");
    when(invitation.getStatus())
        .thenReturn(io.github.lutzseverino.cardo.invite.model.InvitationStatus.PENDING);
    when(invitation.getCreatedAt()).thenReturn(acceptedAt.minusDays(1));
    when(invitation.getExpiresAt()).thenReturn(acceptedAt.plusDays(1));
    when(invitation.accept(acceptedAt)).thenReturn(true);

    assertThat(service(invitations).accept(invitationId, "clinic", acceptedAt)).isTrue();

    verify(invitations).findEntityByIdForUpdate(invitationId);
    verify(invitation).accept(acceptedAt);
  }

  @Test
  void repeatedAcceptanceIsIdempotentWhileHoldingTheSameRowLock() {
    InvitationRepository invitations = mock(InvitationRepository.class);
    Invitation invitation = mock(Invitation.class);
    UUID invitationId = UUID.randomUUID();
    when(invitations.findEntityByIdForUpdate(invitationId)).thenReturn(Optional.of(invitation));
    when(invitation.getProduct()).thenReturn("clinic");
    when(invitation.getStatus())
        .thenReturn(io.github.lutzseverino.cardo.invite.model.InvitationStatus.ACCEPTED);

    assertThat(service(invitations).accept(invitationId, "clinic", OffsetDateTime.now())).isFalse();

    verify(invitations).findEntityByIdForUpdate(invitationId);
    verify(invitation, never()).accept(any());
  }

  @Test
  void rejectsAnAcceptanceTimestampBeforeTheInvitationCreationWindow() {
    InvitationRepository invitations = mock(InvitationRepository.class);
    Invitation invitation = mock(Invitation.class);
    UUID invitationId = UUID.randomUUID();
    OffsetDateTime createdAt = OffsetDateTime.now().minusDays(1);
    when(invitations.findEntityByIdForUpdate(invitationId)).thenReturn(Optional.of(invitation));
    when(invitation.getProduct()).thenReturn("clinic");
    when(invitation.getStatus())
        .thenReturn(io.github.lutzseverino.cardo.invite.model.InvitationStatus.PENDING);
    when(invitation.getCreatedAt()).thenReturn(createdAt);
    InvitationService service = service(invitations);

    assertThatThrownBy(() -> service.accept(invitationId, "clinic", createdAt.minusMinutes(6)))
        .isInstanceOf(ApiException.class)
        .extracting(exception -> ((ApiException) exception).code())
        .isEqualTo("invitation_acceptance_time_invalid");
  }

  @Test
  void rejectsAnAcceptanceTimestampBeyondTheFutureClockSkewWindow() {
    InvitationRepository invitations = mock(InvitationRepository.class);
    Invitation invitation = mock(Invitation.class);
    UUID invitationId = UUID.randomUUID();
    when(invitations.findEntityByIdForUpdate(invitationId)).thenReturn(Optional.of(invitation));
    when(invitation.getProduct()).thenReturn("clinic");
    when(invitation.getStatus())
        .thenReturn(io.github.lutzseverino.cardo.invite.model.InvitationStatus.PENDING);
    when(invitation.getCreatedAt()).thenReturn(OffsetDateTime.now().minusDays(1));
    InvitationService service = service(invitations);

    assertThatThrownBy(
            () -> service.accept(invitationId, "clinic", OffsetDateTime.now().plusMinutes(6)))
        .isInstanceOf(ApiException.class)
        .extracting(exception -> ((ApiException) exception).code())
        .isEqualTo("invitation_acceptance_time_invalid");
  }

  @Test
  void revokesThroughTheLockedInvitationOwner() {
    InvitationDelivery delivery = mock(InvitationDelivery.class);
    InvitationApplicationMapper mapper = mock(InvitationApplicationMapper.class);
    InvitationRepository invitations = mock(InvitationRepository.class);
    Invitation entity = mock(Invitation.class);
    UUID invitationId = UUID.randomUUID();
    when(invitations.findEntityByIdForUpdate(invitationId)).thenReturn(Optional.of(entity));
    when(entity.getProduct()).thenReturn("clinic");
    when(entity.revoke(any(java.time.OffsetDateTime.class))).thenReturn(true);
    InvitationService service =
        new InvitationService(
            delivery,
            mapper,
            new InvitationProperties(Duration.ofDays(1), Duration.ofMinutes(5)),
            invitations);

    org.assertj.core.api.Assertions.assertThat(service.revoke(invitationId, "clinic")).isTrue();

    verify(entity).revoke(any(java.time.OffsetDateTime.class));
  }

  @Test
  void serializesCreateRequestsBeforeCheckingIdempotency() {
    InvitationDelivery delivery = mock(InvitationDelivery.class);
    InvitationApplicationMapper mapper = mock(InvitationApplicationMapper.class);
    InvitationRepository invitations = mock(InvitationRepository.class);
    InvitationService service =
        new InvitationService(
            delivery,
            mapper,
            new InvitationProperties(Duration.ofDays(1), Duration.ofMinutes(5)),
            invitations);
    CreateInvitationInput input = input();

    service.findCreated("clinic", input);

    org.mockito.InOrder order = org.mockito.Mockito.inOrder(invitations);
    order.verify(invitations).lockCreation("clinic", input.requestId());
    order.verify(invitations).findProjectedByProductAndRequestId("clinic", input.requestId());
  }

  @Test
  void stagesInvitationDeliveryInTheOwningTransaction() {
    InvitationDelivery delivery = mock(InvitationDelivery.class);
    InvitationApplicationMapper mapper = mock(InvitationApplicationMapper.class);
    InvitationRepository invitations = mock(InvitationRepository.class);
    Invitation saved = mock(Invitation.class);
    InvitationProjection projection = mock(InvitationProjection.class);
    UUID invitationId = UUID.randomUUID();
    when(saved.getId()).thenReturn(invitationId);
    when(invitations.saveAndFlush(any(Invitation.class))).thenReturn(saved);
    when(invitations.findProjectedById(invitationId)).thenReturn(Optional.of(projection));
    InvitationService service =
        new InvitationService(
            delivery,
            mapper,
            new InvitationProperties(Duration.ofDays(1), Duration.ofMinutes(5)),
            invitations);
    CreateInvitationInput input = input();

    service.create("clinic", input, UUID.randomUUID());

    verify(delivery).stage(invitationId);
  }

  @Test
  void tokenIsExpiredAtTheExactDeadline() {
    OffsetDateTime deadline = OffsetDateTime.parse("2030-07-17T10:00:00Z");
    InvitationRepository invitations = mock(InvitationRepository.class);
    InvitationProjection projection = mock(InvitationProjection.class);
    when(invitations.findProjectedByToken("token-1")).thenReturn(Optional.of(projection));
    when(projection.getStatus())
        .thenReturn(io.github.lutzseverino.cardo.invite.model.InvitationStatus.PENDING);
    when(projection.getExpiresAt()).thenReturn(deadline);

    assertThatThrownBy(
            () ->
                service(invitations, Clock.fixed(deadline.toInstant(), ZoneOffset.UTC))
                    .get("token-1"))
        .isInstanceOf(ApiException.class)
        .extracting(exception -> ((ApiException) exception).code())
        .isEqualTo("invitation_expired");
  }

  private CreateInvitationInput input() {
    return new CreateInvitationInput(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "clinic:clinic",
        "user@example.com",
        UUID.randomUUID(),
        URI.create("https://app.example.com/invitations"));
  }

  private InvitationService service(InvitationRepository invitations) {
    return new InvitationService(
        mock(InvitationDelivery.class),
        mock(InvitationApplicationMapper.class),
        new InvitationProperties(Duration.ofDays(1), Duration.ofMinutes(5)),
        invitations);
  }

  private InvitationService service(InvitationRepository invitations, Clock clock) {
    return new InvitationService(
        clock,
        new SecureRandom(),
        mock(InvitationDelivery.class),
        mock(InvitationApplicationMapper.class),
        new InvitationProperties(Duration.ofDays(1), Duration.ofMinutes(5)),
        invitations);
  }
}
