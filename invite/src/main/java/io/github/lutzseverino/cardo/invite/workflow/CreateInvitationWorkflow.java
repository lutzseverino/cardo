package io.github.lutzseverino.cardo.invite.workflow;

import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.identity.client.IdentityUsersClient;
import io.github.lutzseverino.cardo.identity.client.ProvisionalUser;
import io.github.lutzseverino.cardo.invite.model.CreateInvitationInput;
import io.github.lutzseverino.cardo.invite.model.CreateInvitationResult;
import io.github.lutzseverino.cardo.invite.service.InvitationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Validated
@Component
@RequiredArgsConstructor
public class CreateInvitationWorkflow {

  private final IdentityUsersClient identityUsers;
  private final InvitationService invitations;

  @Transactional
  public CreateInvitationResult create(
      @NotBlank String product, @Valid CreateInvitationInput input) {
    requireProductOwnership(product, input);
    var existing = invitations.findCreated(product, input);
    if (existing.isPresent()) {
      return existing.orElseThrow();
    }
    ProvisionalUser invited = identityUsers.createProvisional(input.email());
    return invitations.create(product, input, invited.id());
  }

  private void requireProductOwnership(String product, CreateInvitationInput input) {
    String prefix = product + ":";
    if (!input.tenantResourceType().startsWith(prefix)) {
      throw ApiException.forbidden(
          "invitation_product_mismatch",
          "Invitation resources and access profiles must belong to the calling product.");
    }
  }
}
