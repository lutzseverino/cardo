package io.github.lutzseverino.cardo.invite.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.invite.config.InvitationProperties;
import io.github.lutzseverino.cardo.invite.mapper.InvitationApplicationMapper;
import io.github.lutzseverino.cardo.invite.model.CreateInvitationInput;
import io.github.lutzseverino.cardo.invite.model.Invitation;
import io.github.lutzseverino.cardo.invite.model.InvitationGrantInput;
import io.github.lutzseverino.cardo.invite.provider.InvitationDelivery;
import io.github.lutzseverino.cardo.invite.repository.InvitationProjection;
import io.github.lutzseverino.cardo.invite.repository.InvitationRepository;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvitationServiceTest {

  @Test
  void rejectsAnAcceptanceTimestampBeforeTheInvitationCreationWindow() {
    InvitationRepository invitations = mock(InvitationRepository.class);
    InvitationProjection projection = mock(InvitationProjection.class);
    UUID invitationId = UUID.randomUUID();
    OffsetDateTime createdAt = OffsetDateTime.now().minusDays(1);
    when(invitations.findProjectedById(invitationId)).thenReturn(Optional.of(projection));
    when(projection.getProduct()).thenReturn("clinic");
    when(projection.getCreatedAt()).thenReturn(createdAt);
    InvitationService service = service(invitations);

    assertThatThrownBy(
            () -> service.requirePending(invitationId, "clinic", createdAt.minusMinutes(6)))
        .isInstanceOf(ApiException.class)
        .extracting(exception -> ((ApiException) exception).code())
        .isEqualTo("invitation_acceptance_time_invalid");
  }

  @Test
  void rejectsAnAcceptanceTimestampBeyondTheFutureClockSkewWindow() {
    InvitationRepository invitations = mock(InvitationRepository.class);
    InvitationProjection projection = mock(InvitationProjection.class);
    UUID invitationId = UUID.randomUUID();
    when(invitations.findProjectedById(invitationId)).thenReturn(Optional.of(projection));
    when(projection.getProduct()).thenReturn("clinic");
    when(projection.getCreatedAt()).thenReturn(OffsetDateTime.now().minusDays(1));
    InvitationService service = service(invitations);

    assertThatThrownBy(
            () ->
                service.requirePending(invitationId, "clinic", OffsetDateTime.now().plusMinutes(6)))
        .isInstanceOf(ApiException.class)
        .extracting(exception -> ((ApiException) exception).code())
        .isEqualTo("invitation_acceptance_time_invalid");
  }

  @Test
  void revokesThroughTheInvitationOwnerAndReturnsTheUpdatedProjection() {
    InvitationDelivery delivery = mock(InvitationDelivery.class);
    InvitationApplicationMapper mapper = mock(InvitationApplicationMapper.class);
    InvitationRepository invitations = mock(InvitationRepository.class);
    Invitation entity = mock(Invitation.class);
    InvitationProjection projection = mock(InvitationProjection.class);
    io.github.lutzseverino.cardo.invite.model.InvitationResult result =
        mock(io.github.lutzseverino.cardo.invite.model.InvitationResult.class);
    UUID invitationId = UUID.randomUUID();
    when(invitations.findEntityById(invitationId)).thenReturn(Optional.of(entity));
    when(entity.getProduct()).thenReturn("clinic");
    when(invitations.findProjectedById(invitationId)).thenReturn(Optional.of(projection));
    when(mapper.toResult(projection)).thenReturn(result);
    InvitationService service =
        new InvitationService(
            delivery,
            mapper,
            new InvitationProperties(Duration.ofDays(1), Duration.ofMinutes(5)),
            invitations);

    org.assertj.core.api.Assertions.assertThat(service.revoke(invitationId, "clinic"))
        .isSameAs(result);

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

    service.create("clinic", input, UUID.randomUUID(), "subject-1");

    verify(delivery).stage(invitationId);
  }

  private CreateInvitationInput input() {
    return new CreateInvitationInput(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "clinic:clinic",
        "user@example.com",
        "clinic:employee",
        List.of(new InvitationGrantInput("clinic:clinic", "read")),
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
}
