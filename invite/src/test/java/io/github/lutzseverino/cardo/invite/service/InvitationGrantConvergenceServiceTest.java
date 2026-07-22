package io.github.lutzseverino.cardo.invite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.authorization.grant.GrantReceipt;
import io.github.lutzseverino.cardo.authorization.grant.GrantReceiptStatus;
import io.github.lutzseverino.cardo.authorization.grant.Grants;
import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.invite.model.InvitationGrantConvergenceStatus;
import io.github.lutzseverino.cardo.invite.model.InvitationStatus;
import io.github.lutzseverino.cardo.invite.repository.InvitationProjection;
import io.github.lutzseverino.cardo.invite.repository.InvitationRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class InvitationGrantConvergenceServiceTest {

  private final Grants grants = mock(Grants.class);
  private final InvitationRepository invitations = mock(InvitationRepository.class);
  private final InvitationGrantConvergenceService service =
      new InvitationGrantConvergenceService(grants, invitations);
  private final UUID invitationId = UUID.randomUUID();

  @ParameterizedTest
  @EnumSource(GrantReceiptStatus.class)
  void mapsKnownGrantReceiptStatesWithoutExposingTheReceiptId(GrantReceiptStatus status) {
    UUID receiptId = UUID.randomUUID();
    InvitationProjection invitation = accepted(receiptId);
    String failureCode = GrantReceiptStatus.FAILED.equals(status) ? "provider_failed" : null;
    when(grants.find(receiptId))
        .thenReturn(Optional.of(new GrantReceipt(receiptId, status, failureCode)));

    var result = service.get(invitationId, "clinic");

    assertThat(result.invitationId()).isEqualTo(invitationId);
    assertThat(result.status()).isEqualTo(InvitationGrantConvergenceStatus.valueOf(status.name()));
    assertThat(result.failureCode()).isEqualTo(failureCode);
    assertThat(result.toString()).doesNotContain(receiptId.toString());
  }

  @Test
  void mapsAcceptedLegacyInvitationWithoutReceiptToUnknown() {
    accepted(null);

    assertThat(service.get(invitationId, "clinic").status())
        .isEqualTo(InvitationGrantConvergenceStatus.UNKNOWN);
  }

  @Test
  void treatsAnUnknownStoredReceiptAsAnIntegrityFailure() {
    UUID receiptId = UUID.randomUUID();
    accepted(receiptId);
    when(grants.find(receiptId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(invitationId, "clinic"))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> {
              assertThat(exception.status()).isEqualTo(500);
              assertThat(exception.code()).isEqualTo("invitation_grant_receipt_missing");
            });
  }

  @ParameterizedTest
  @EnumSource(
      value = InvitationStatus.class,
      names = {"PENDING", "REVOKED"})
  void rejectsConvergenceForInvitationsThatWereNotAccepted(InvitationStatus status) {
    InvitationProjection invitation = mock(InvitationProjection.class);
    when(invitations.findProjectedById(invitationId)).thenReturn(Optional.of(invitation));
    when(invitation.getProduct()).thenReturn("clinic");
    when(invitation.getStatus()).thenReturn(status);

    assertThatThrownBy(() -> service.get(invitationId, "clinic"))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.code()).isEqualTo("invitation_grant_convergence_unavailable"));
  }

  @Test
  void rejectsConvergenceForAnotherProduct() {
    InvitationProjection invitation = accepted(UUID.randomUUID());

    assertThatThrownBy(() -> service.get(invitationId, "polity"))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> assertThat(exception.code()).isEqualTo("invitation_product_mismatch"));
  }

  @Test
  void reportsAMissingInvitation() {
    assertThatThrownBy(() -> service.get(invitationId, "clinic"))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> assertThat(exception.code()).isEqualTo("invitation_not_found"));
  }

  private InvitationProjection accepted(UUID receiptId) {
    InvitationProjection invitation = mock(InvitationProjection.class);
    when(invitations.findProjectedById(invitationId)).thenReturn(Optional.of(invitation));
    when(invitation.getProduct()).thenReturn("clinic");
    when(invitation.getStatus()).thenReturn(InvitationStatus.ACCEPTED);
    when(invitation.getGrantReceiptId()).thenReturn(receiptId);
    return invitation;
  }
}
