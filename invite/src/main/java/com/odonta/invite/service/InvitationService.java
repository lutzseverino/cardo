package com.odonta.invite.service;

import com.odonta.authorization.access.AccessProfileService;
import com.odonta.authorization.grant.Grants;
import com.odonta.authorization.spring.AuthenticatedUser;
import com.odonta.common.api.ApiException;
import com.odonta.common.model.EmailAddress;
import com.odonta.identity.client.IdentityUsersClient;
import com.odonta.identity.client.ProvisionalUser;
import com.odonta.invite.InvitePermissions;
import com.odonta.invite.api.model.CompleteInvitationInput;
import com.odonta.invite.api.model.CreateInvitationInput;
import com.odonta.invite.authorization.InvitationGrantPlanner;
import com.odonta.invite.config.InvitationProperties;
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
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Validated
@Service
@RequiredArgsConstructor
public class InvitationService {

  private final Clock clock = Clock.systemUTC();
  private final SecureRandom random = new SecureRandom();
  private final AccessProfileService accessProfiles;
  private final EmailSender email;
  private final Grants grants;
  private final IdentityUsersClient identityUsers;
  private final InvitationGrantPlanner invitationGrantPlanner;
  private final InvitationProperties properties;
  private final InvitationRepository invitations;

  public InvitationProjection get(String token) {
    return validInvitation(token);
  }

  @Transactional
  @PreAuthorize(
      "hasPermission(#input.tenantId, #input.tenantResourceType, '"
          + InvitePermissions.WRITE
          + "')")
  public CreateInvitationResult create(
      AuthenticatedUser inviter, @Valid CreateInvitationInput input) {
    accessProfiles
        .availableProfile(
            input.getAccessProfileId(), product(input.getTenantResourceType()), input.getTenantId())
        .orElseThrow(
            () -> ApiException.notFound("access_profile_not_found", "Access profile not found."));
    ProvisionalUser invited = identityUsers.createProvisional(input.getEmail());
    try {
      String token = generateInvitationToken();
      Invitation invitation =
          invitations.saveAndFlush(
              new Invitation(
                  input.getTenantId(),
                  input.getTenantResourceType(),
                  input.getAccessProfileId(),
                  EmailAddress.of(input.getEmail()).value(),
                  invited.id(),
                  invited.authorizationSubject(),
                  inviter.id(),
                  token));
      String acceptUrl = "%s/invitations/%s".formatted(properties.webUrl(), token);
      email.sendInvitation(input.getEmail(), acceptUrl);
      return new CreateInvitationResult(getProjection(invitation.getId()), acceptUrl);
    } catch (RuntimeException exception) {
      cancelProvisionalUser(invited.id(), exception);
      throw exception;
    }
  }

  @Transactional
  public void complete(String token, @Valid CompleteInvitationInput input) {
    InvitationProjection invitation = validInvitation(token);
    ProvisionalUser completed =
        identityUsers.completeProvisional(
            invitation.getInvitedUserId(), input.getName(), input.getPassword());
    accept(invitation, completed.id(), completed.authorizationSubject());
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
        invitationGrantPlanner.acceptance(
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

  private String product(String tenantResourceType) {
    return tenantResourceType.substring(0, tenantResourceType.indexOf(':'));
  }

  private String generateInvitationToken() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private void cancelProvisionalUser(UUID userId, RuntimeException original) {
    try {
      identityUsers.cancelProvisional(userId);
    } catch (RuntimeException compensationFailure) {
      original.addSuppressed(compensationFailure);
    }
  }
}
