package io.github.lutzseverino.cardo.invite.service;

import io.github.lutzseverino.cardo.authorization.grant.GrantReceipt;
import io.github.lutzseverino.cardo.authorization.grant.Grants;
import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.invite.model.InvitationGrantConvergenceResult;
import io.github.lutzseverino.cardo.invite.model.InvitationGrantConvergenceStatus;
import io.github.lutzseverino.cardo.invite.model.InvitationStatus;
import io.github.lutzseverino.cardo.invite.repository.InvitationProjection;
import io.github.lutzseverino.cardo.invite.repository.InvitationRepository;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Validated
@Service
@RequiredArgsConstructor
public class InvitationGrantConvergenceService {

  private final Grants grants;
  private final InvitationRepository invitations;

  @Transactional(readOnly = true)
  public InvitationGrantConvergenceResult get(UUID invitationId, @NotBlank String product) {
    InvitationProjection invitation =
        invitations
            .findProjectedById(invitationId)
            .orElseThrow(
                () -> ApiException.notFound("invitation_not_found", "Invitation not found."));
    if (!invitation.getProduct().equals(product)) {
      throw ApiException.forbidden(
          "invitation_product_mismatch", "This invitation belongs to another product.");
    }
    if (!InvitationStatus.ACCEPTED.equals(invitation.getStatus())) {
      throw ApiException.conflict(
          "invitation_grant_convergence_unavailable",
          "Grant convergence is available only for accepted invitations.");
    }
    if (invitation.getGrantReceiptId() == null) {
      return new InvitationGrantConvergenceResult(
          invitationId, InvitationGrantConvergenceStatus.UNKNOWN, null);
    }
    GrantReceipt receipt =
        grants
            .find(invitation.getGrantReceiptId())
            .orElseThrow(
                () ->
                    ApiException.of(
                        500,
                        "invitation_grant_receipt_missing",
                        "The invitation grant receipt could not be resolved."));
    return new InvitationGrantConvergenceResult(
        invitationId,
        InvitationGrantConvergenceStatus.valueOf(receipt.status().name()),
        receipt.failureCode());
  }
}
