package com.odonta.invite.service;

import com.odonta.authorization.access.AccessProfileService;
import com.odonta.authorization.grant.Grants;
import com.odonta.authorization.spring.AuthenticatedUser;
import com.odonta.common.api.ApiException;
import com.odonta.common.model.EmailAddress;
import com.odonta.identity.client.CompleteProvisionalUserRequest;
import com.odonta.identity.client.CreateProvisionalUserRequest;
import com.odonta.identity.client.UserResponse;
import com.odonta.identity.client.api.UsersApi;
import com.odonta.invite.InvitePermissions;
import com.odonta.invite.authorization.InvitationGrants;
import com.odonta.invite.config.InvitationProperties;
import com.odonta.invite.model.CompleteInvitationCommand;
import com.odonta.invite.model.CreateInvitationCommand;
import com.odonta.invite.model.CreateInvitationResult;
import com.odonta.invite.model.Invitation;
import com.odonta.invite.model.InvitationProjection;
import com.odonta.invite.model.InvitationStatus;
import com.odonta.invite.repository.InvitationRepository;
import jakarta.validation.Valid;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Validated
@Service
public class InvitationService {

  private final Clock clock = Clock.systemUTC();
  private final SecureRandom random = new SecureRandom();
  private final AccessProfileService accessProfiles;
  private final EmailSender email;
  private final Grants grants;
  private final UsersApi identityUsers;
  private final InvitationGrants invitationGrants;
  private final InvitationProperties properties;
  private final InvitationRepository invitations;

  InvitationService(
      AccessProfileService accessProfiles,
      EmailSender email,
      Grants grants,
      UsersApi identityUsers,
      InvitationGrants invitationGrants,
      InvitationProperties properties,
      InvitationRepository invitations) {
    this.accessProfiles = accessProfiles;
    this.email = email;
    this.grants = grants;
    this.identityUsers = identityUsers;
    this.invitationGrants = invitationGrants;
    this.properties = properties;
    this.invitations = invitations;
  }

  public InvitationProjection get(String token) {
    return validInvitation(token);
  }

  @Transactional
  @PreAuthorize(
      "hasPermission(#command.tenantId, #command.tenantResourceType, '"
          + InvitePermissions.WRITE
          + "')")
  public CreateInvitationResult create(
      AuthenticatedUser inviter, @Valid CreateInvitationCommand command) {
    accessProfiles
        .availableProfile(command.accessProfileId(), command.product(), command.tenantId())
        .orElseThrow(
            () -> ApiException.notFound("access_profile_not_found", "Access profile not found."));
    UserResponse invited =
        identityUsers.createProvisionalUser(
            new CreateProvisionalUserRequest().email(command.email()));
    try {
      String token = generateInvitationToken();
      Invitation invitation =
          invitations.saveAndFlush(
              new Invitation(
                  command.tenantId(),
                  command.tenantResourceType(),
                  command.accessProfileId(),
                  EmailAddress.of(command.email()).value(),
                  invited.getId(),
                  invited.getAuthorizationSubject(),
                  inviter.id(),
                  token));
      String acceptUrl = "%s/invitations/%s".formatted(properties.webUrl(), token);
      email.sendInvitation(command.email(), acceptUrl);
      return new CreateInvitationResult(getProjection(invitation.getId()), acceptUrl);
    } catch (RuntimeException exception) {
      cancelProvisionalUser(invited.getId(), exception);
      throw exception;
    }
  }

  @Transactional
  public void complete(String token, @Valid CompleteInvitationCommand command) {
    InvitationProjection invitation = validInvitation(token);
    UserResponse completed =
        identityUsers.completeProvisionalUser(
            invitation.getInvitedUserId(),
            new CompleteProvisionalUserRequest().name(command.name()).password(command.password()));
    accept(invitation, completed.getId(), completed.getAuthorizationSubject());
  }

  @Transactional
  public void accept(String token, AuthenticatedUser user) {
    InvitationProjection invitation = validInvitation(token);
    if (!invitation.getInvitedUserId().equals(user.id())) {
      throw ApiException.forbidden(
          "invitation_user_mismatch", "This invitation was created for another user.");
    }
    accept(invitation, user.id(), user.authorizationSubject());
  }

  private void accept(InvitationProjection invitation, UUID userId, String authorizationSubject) {
    Invitation entity =
        invitations
            .findById(invitation.getId())
            .orElseThrow(
                () -> ApiException.notFound("invitation_not_found", "Invitation not found."));
    entity.accept(OffsetDateTime.now(clock));
    grants.stage(
        invitationGrants.acceptance(
            invitation.getTenantId(),
            invitation.getTenantResourceType(),
            authorizationSubject,
            accessProfiles.grants(invitation.getAccessProfileId())));
  }

  private InvitationProjection validInvitation(String token) {
    InvitationProjection invitation =
        invitations
            .findProjectedByToken(token)
            .orElseThrow(
                () -> ApiException.notFound("invitation_not_found", "Invitation not found."));
    if (!InvitationStatus.PENDING.equals(invitation.getStatus())) {
      throw ApiException.gone("invitation_unavailable", "Invitation is no longer available.");
    }
    if (invitation.getCreatedAt().plus(properties.ttl()).isBefore(OffsetDateTime.now(clock))) {
      throw ApiException.gone("invitation_expired", "Invitation expired.");
    }
    return invitation;
  }

  private InvitationProjection getProjection(UUID id) {
    return invitations
        .findProjectedById(id)
        .orElseThrow(() -> ApiException.notFound("invitation_not_found", "Invitation not found."));
  }

  private String generateInvitationToken() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private void cancelProvisionalUser(UUID userId, RuntimeException original) {
    try {
      identityUsers.cancelProvisionalUser(userId);
    } catch (RuntimeException compensationFailure) {
      original.addSuppressed(compensationFailure);
    }
  }
}
