package io.github.lutzseverino.cardo.invite.client.http;

import io.github.lutzseverino.cardo.invite.client.InvitationGrantConvergence;
import io.github.lutzseverino.cardo.invite.client.InvitationGrantConvergenceClient;
import io.github.lutzseverino.cardo.invite.client.InvitationGrantConvergenceStatus;
import io.github.lutzseverino.cardo.invite.client.http.generated.InvitationGrantConvergenceResponse;
import io.github.lutzseverino.cardo.invite.client.http.generated.api.InvitationGrantConvergenceApi;
import java.util.UUID;

final class HttpInvitationGrantConvergenceClient implements InvitationGrantConvergenceClient {

  private final InvitationGrantConvergenceApi convergence;

  HttpInvitationGrantConvergenceClient(InvitationGrantConvergenceApi convergence) {
    this.convergence = convergence;
  }

  @Override
  public InvitationGrantConvergence get(UUID invitationId) {
    InvitationGrantConvergenceResponse response =
        convergence.getInvitationGrantConvergence(invitationId);
    return new InvitationGrantConvergence(
        response.getInvitationId(),
        InvitationGrantConvergenceStatus.valueOf(response.getStatus().name()),
        response.getFailureCode());
  }
}
