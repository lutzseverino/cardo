package io.github.lutzseverino.cardo.invite.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;

public record CreateInvitationInput(
    @NotNull UUID requestId,
    @NotNull UUID tenantId,
    @NotBlank @Pattern(regexp = "^[^:]+:[^:]+$") String tenantResourceType,
    @NotBlank @Email String email,
    @NotBlank @Pattern(regexp = "^[^:]+:.+$") String accessProfile,
    @NotNull @Size(min = 1) List<@Valid InvitationGrantInput> grants,
    @NotNull UUID invitedBy,
    @NotNull URI acceptUrlBase) {

  public CreateInvitationInput {
    grants = grants == null ? null : List.copyOf(grants);
  }

  @AssertTrue(message = "acceptUrlBase must be an absolute HTTP(S) URL without query or fragment")
  public boolean isAcceptUrlBaseValid() {
    return acceptUrlBase == null
        || (("http".equalsIgnoreCase(acceptUrlBase.getScheme())
                || "https".equalsIgnoreCase(acceptUrlBase.getScheme()))
            && acceptUrlBase.getHost() != null
            && !acceptUrlBase.getHost().isBlank()
            && acceptUrlBase.getQuery() == null
            && acceptUrlBase.getFragment() == null);
  }
}
