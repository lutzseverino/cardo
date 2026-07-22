package io.github.lutzseverino.cardo.invite.model;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.UUID;

public record CreateInvitationInput(
    @NotNull UUID requestId,
    @NotNull UUID tenantId,
    @NotBlank @Pattern(regexp = "^[^:]+:[^:]+$") String tenantResourceType,
    @NotBlank @Email String email,
    @NotNull UUID invitedBy,
    @NotNull URI acceptUrlBase) {

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
