package io.github.lutzseverino.cardo.invite.client.http;

import io.github.lutzseverino.cardo.invite.client.CreateInvitation;
import io.github.lutzseverino.cardo.invite.client.CreatedInvitation;
import io.github.lutzseverino.cardo.invite.client.Invitation;
import io.github.lutzseverino.cardo.invite.client.InvitationCompletion;
import io.github.lutzseverino.cardo.invite.client.InvitationCompletionStatus;
import io.github.lutzseverino.cardo.invite.client.InvitationStatus;
import io.github.lutzseverino.cardo.invite.client.InvitationToken;
import io.github.lutzseverino.cardo.invite.client.InvitationsClient;
import io.github.lutzseverino.cardo.invite.client.http.generated.AcceptInvitationRequest;
import io.github.lutzseverino.cardo.invite.client.http.generated.CreateInvitationRequest;
import io.github.lutzseverino.cardo.invite.client.http.generated.CreateInvitationResponse;
import io.github.lutzseverino.cardo.invite.client.http.generated.InvitationCompletionResponse;
import io.github.lutzseverino.cardo.invite.client.http.generated.InvitationGrantRequest;
import io.github.lutzseverino.cardo.invite.client.http.generated.InvitationResponse;
import io.github.lutzseverino.cardo.invite.client.http.generated.InvitationTokenResponse;
import io.github.lutzseverino.cardo.invite.client.http.generated.api.InvitationTokensApi;
import io.github.lutzseverino.cardo.invite.client.http.generated.api.InvitationsApi;
import java.time.OffsetDateTime;
import java.util.UUID;

final class HttpInvitationsClient implements InvitationsClient {

  private final InvitationsApi invitations;
  private final InvitationTokensApi tokens;

  HttpInvitationsClient(InvitationsApi invitations, InvitationTokensApi tokens) {
    this.invitations = invitations;
    this.tokens = tokens;
  }

  @Override
  public CreatedInvitation create(CreateInvitation input) {
    CreateInvitationRequest request =
        new CreateInvitationRequest()
            .requestId(input.requestId())
            .tenantId(input.tenantId())
            .tenantResourceType(input.tenantResourceType())
            .email(input.email())
            .accessProfile(input.accessProfile())
            .grants(
                input.grants().stream()
                    .map(
                        grant ->
                            new InvitationGrantRequest()
                                .resourceType(grant.resourceType())
                                .action(grant.action()))
                    .toList())
            .invitedBy(input.invitedBy())
            .acceptUrlBase(input.acceptUrlBase());
    CreateInvitationResponse response = invitations.createInvitation(request);
    return new CreatedInvitation(toInvitation(response.getInvitation()), response.getAcceptUrl());
  }

  @Override
  public Invitation get(UUID invitationId) {
    return toInvitation(invitations.getInvitation(invitationId));
  }

  @Override
  public InvitationToken getByToken(String token) {
    InvitationTokenResponse response = tokens.getInvitationByToken(token);
    return new InvitationToken(
        response.getId(),
        response.getTenantId(),
        response.getTenantResourceType(),
        response.getInvitedEmail(),
        response.getExpiresAt());
  }

  @Override
  public InvitationCompletion requestCompletion(String token) {
    return toCompletion(tokens.requestInvitationCompletion(token));
  }

  @Override
  public InvitationCompletion getCompletion(String token) {
    return toCompletion(tokens.getInvitationCompletion(token));
  }

  @Override
  public Invitation accept(UUID invitationId, OffsetDateTime acceptedAt) {
    return toInvitation(
        invitations.acceptInvitation(
            invitationId, new AcceptInvitationRequest().acceptedAt(acceptedAt)));
  }

  @Override
  public Invitation revoke(UUID invitationId) {
    return toInvitation(invitations.revokeInvitation(invitationId));
  }

  private Invitation toInvitation(InvitationResponse response) {
    return new Invitation(
        response.getId(),
        response.getRequestId(),
        response.getTenantId(),
        response.getTenantResourceType(),
        response.getAccessProfile(),
        response.getInvitedEmail(),
        response.getInvitedUserId(),
        response.getInvitedBy(),
        toStatus(response.getStatus()),
        response.getExpiresAt(),
        response.getAcceptedAt(),
        response.getRevokedAt(),
        response.getCreatedAt(),
        response.getUpdatedAt());
  }

  private InvitationStatus toStatus(
      io.github.lutzseverino.cardo.invite.client.http.generated.InvitationStatus status) {
    return switch (status) {
      case PENDING -> InvitationStatus.PENDING;
      case ACCEPTED -> InvitationStatus.ACCEPTED;
      case REVOKED -> InvitationStatus.REVOKED;
    };
  }

  private InvitationCompletion toCompletion(InvitationCompletionResponse response) {
    return new InvitationCompletion(
        response.getId(),
        response.getInvitationId(),
        response.getInvitedUserId(),
        InvitationCompletionStatus.valueOf(response.getStatus().name()),
        response.getAttemptCount(),
        response.getLastError(),
        response.getActionExpiresAt(),
        response.getCompletedAt(),
        response.getCreatedAt(),
        response.getUpdatedAt());
  }
}
