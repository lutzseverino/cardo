package io.github.lutzseverino.cardo.invite.service;

import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.common.model.EmailAddress;
import io.github.lutzseverino.cardo.invite.config.InvitationProperties;
import io.github.lutzseverino.cardo.invite.mapper.InvitationApplicationMapper;
import io.github.lutzseverino.cardo.invite.model.CreateInvitationInput;
import io.github.lutzseverino.cardo.invite.model.CreateInvitationResult;
import io.github.lutzseverino.cardo.invite.model.Invitation;
import io.github.lutzseverino.cardo.invite.model.InvitationResult;
import io.github.lutzseverino.cardo.invite.model.InvitationStatus;
import io.github.lutzseverino.cardo.invite.model.InvitationTokenResult;
import io.github.lutzseverino.cardo.invite.model.PendingInvitation;
import io.github.lutzseverino.cardo.invite.provider.InvitationDelivery;
import io.github.lutzseverino.cardo.invite.repository.InvitationProjection;
import io.github.lutzseverino.cardo.invite.repository.InvitationRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Validated
@Service
public class InvitationService {

  private final Clock clock;
  private final SecureRandom random;
  private final InvitationDelivery delivery;
  private final InvitationApplicationMapper mapper;
  private final InvitationProperties properties;
  private final InvitationRepository invitations;

  @Autowired
  public InvitationService(
      InvitationDelivery delivery,
      InvitationApplicationMapper mapper,
      InvitationProperties properties,
      InvitationRepository invitations) {
    this(Clock.systemUTC(), new SecureRandom(), delivery, mapper, properties, invitations);
  }

  InvitationService(
      Clock clock,
      SecureRandom random,
      InvitationDelivery delivery,
      InvitationApplicationMapper mapper,
      InvitationProperties properties,
      InvitationRepository invitations) {
    this.clock = clock;
    this.random = random;
    this.delivery = delivery;
    this.mapper = mapper;
    this.properties = properties;
    this.invitations = invitations;
  }

  public InvitationTokenResult get(@NotBlank String token) {
    InvitationProjection invitation = requirePendingProjection(token);
    return new InvitationTokenResult(
        invitation.getId(),
        invitation.getTenantId(),
        invitation.getTenantResourceType(),
        invitation.getInvitedEmail(),
        invitation.getExpiresAt());
  }

  public InvitationResult get(UUID invitationId, @NotBlank String product) {
    InvitationProjection invitation = getProjection(invitationId);
    requireOwner(invitation.getProduct(), product);
    return mapper.toResult(invitation);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<CreateInvitationResult> findCreated(
      @NotBlank String product, @Valid CreateInvitationInput input) {
    invitations.lockCreation(product, input.requestId());
    return invitations
        .findProjectedByProductAndRequestId(product, input.requestId())
        .map(
            invitation -> {
              requireSameRequest(invitation, product, input);
              return toCreateResult(invitation);
            });
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public CreateInvitationResult create(
      @NotBlank String product, @Valid CreateInvitationInput input, UUID invitedUserId) {
    String token = generateToken();
    OffsetDateTime expiresAt = OffsetDateTime.now(clock).plus(properties.ttl());
    Invitation invitation =
        invitations.saveAndFlush(
            new Invitation(
                input.requestId(),
                product,
                input.tenantId(),
                input.tenantResourceType(),
                EmailAddress.of(input.email()).value(),
                invitedUserId,
                input.invitedBy(),
                input.acceptUrlBase(),
                expiresAt,
                token));
    String acceptUrl = invitationUrl(input.acceptUrlBase(), token);
    delivery.stage(invitation.getId());
    return new CreateInvitationResult(
        mapper.toResult(getProjection(invitation.getId())), acceptUrl);
  }

  public PendingInvitation requirePending(@NotBlank String token, @NotBlank String product) {
    InvitationProjection invitation =
        invitations
            .findProjectedByToken(token)
            .orElseThrow(
                () -> ApiException.notFound("invitation_not_found", "Invitation not found."));
    requireOwner(invitation.getProduct(), product);
    requirePending(invitation);
    return toPending(invitation);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public PendingInvitation requirePendingForUpdate(
      @NotBlank String token, @NotBlank String product) {
    Invitation invitation =
        invitations
            .findEntityByTokenForUpdate(token)
            .orElseThrow(
                () -> ApiException.notFound("invitation_not_found", "Invitation not found."));
    requireOwner(invitation.getProduct(), product);
    if (!InvitationStatus.PENDING.equals(invitation.getStatus())) {
      throw ApiException.gone("invitation_unavailable", "Invitation is no longer available.");
    }
    if (!invitation.getExpiresAt().isAfter(OffsetDateTime.now(clock))) {
      throw ApiException.gone("invitation_expired", "Invitation expired.");
    }
    return new PendingInvitation(
        invitation.getId(),
        invitation.getProduct(),
        invitation.getTenantId(),
        invitation.getTenantResourceType(),
        invitation.getInvitedUserId(),
        invitation.getExpiresAt());
  }

  public UUID requireOwnedId(@NotBlank String token, @NotBlank String product) {
    InvitationProjection invitation =
        invitations
            .findProjectedByToken(token)
            .orElseThrow(
                () -> ApiException.notFound("invitation_not_found", "Invitation not found."));
    requireOwner(invitation.getProduct(), product);
    return invitation.getId();
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public boolean accept(UUID invitationId, @NotBlank String product, OffsetDateTime acceptedAt) {
    Invitation invitation = getEntityForUpdate(invitationId);
    requireOwner(invitation.getProduct(), product);
    if (InvitationStatus.ACCEPTED.equals(invitation.getStatus())) {
      return false;
    }
    requirePlausibleAcceptanceTime(invitation, acceptedAt);
    requirePending(invitation, acceptedAt);
    return invitation.accept(acceptedAt);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public boolean revoke(UUID invitationId, @NotBlank String product) {
    Invitation invitation = getEntityForUpdate(invitationId);
    requireOwner(invitation.getProduct(), product);
    return invitation.revoke(OffsetDateTime.now(clock));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public InvitationStatus lockStatus(UUID invitationId) {
    return getEntityForUpdate(invitationId).getStatus();
  }

  private InvitationProjection requirePendingProjection(String token) {
    InvitationProjection invitation =
        invitations
            .findProjectedByToken(token)
            .orElseThrow(
                () -> ApiException.notFound("invitation_not_found", "Invitation not found."));
    if (!InvitationStatus.PENDING.equals(invitation.getStatus())) {
      throw ApiException.gone("invitation_unavailable", "Invitation is no longer available.");
    }
    if (!invitation.getExpiresAt().isAfter(OffsetDateTime.now(clock))) {
      throw ApiException.gone("invitation_expired", "Invitation expired.");
    }
    return invitation;
  }

  private InvitationProjection getProjection(UUID id) {
    return invitations
        .findProjectedById(id)
        .orElseThrow(() -> ApiException.notFound("invitation_not_found", "Invitation not found."));
  }

  private Invitation getEntity(UUID id) {
    return invitations
        .findEntityById(id)
        .orElseThrow(() -> ApiException.notFound("invitation_not_found", "Invitation not found."));
  }

  private Invitation getEntityForUpdate(UUID id) {
    return invitations
        .findEntityByIdForUpdate(id)
        .orElseThrow(() -> ApiException.notFound("invitation_not_found", "Invitation not found."));
  }

  private PendingInvitation toPending(InvitationProjection invitation) {
    return new PendingInvitation(
        invitation.getId(),
        invitation.getProduct(),
        invitation.getTenantId(),
        invitation.getTenantResourceType(),
        invitation.getInvitedUserId(),
        invitation.getExpiresAt());
  }

  private PendingInvitation toPending(Invitation invitation) {
    return new PendingInvitation(
        invitation.getId(),
        invitation.getProduct(),
        invitation.getTenantId(),
        invitation.getTenantResourceType(),
        invitation.getInvitedUserId(),
        invitation.getExpiresAt());
  }

  private CreateInvitationResult toCreateResult(InvitationProjection invitation) {
    return new CreateInvitationResult(
        mapper.toResult(invitation),
        invitationUrl(URI.create(invitation.getAcceptUrlBase()), invitation.getToken()));
  }

  private void requirePending(InvitationProjection invitation) {
    requirePending(invitation, OffsetDateTime.now(clock));
  }

  private void requirePending(InvitationProjection invitation, OffsetDateTime effectiveAt) {
    if (!InvitationStatus.PENDING.equals(invitation.getStatus())) {
      throw ApiException.gone("invitation_unavailable", "Invitation is no longer available.");
    }
    if (!invitation.getExpiresAt().isAfter(effectiveAt)) {
      throw ApiException.gone("invitation_expired", "Invitation expired.");
    }
  }

  private void requirePending(Invitation invitation, OffsetDateTime effectiveAt) {
    if (!InvitationStatus.PENDING.equals(invitation.getStatus())) {
      if (InvitationStatus.REVOKED.equals(invitation.getStatus())) {
        throw ApiException.conflict(
            "invitation_revoked", "A revoked invitation cannot be accepted.");
      }
      throw ApiException.gone("invitation_unavailable", "Invitation is no longer available.");
    }
    if (!invitation.getExpiresAt().isAfter(effectiveAt)) {
      throw ApiException.gone("invitation_expired", "Invitation expired.");
    }
  }

  private void requirePlausibleAcceptanceTime(
      InvitationProjection invitation, OffsetDateTime acceptedAt) {
    Duration skew = properties.acceptanceClockSkew();
    OffsetDateTime now = OffsetDateTime.now(clock);
    if (acceptedAt.isBefore(invitation.getCreatedAt().minus(skew))
        || acceptedAt.isAfter(now.plus(skew))) {
      throw ApiException.badRequest(
          "invitation_acceptance_time_invalid",
          "The product acceptance time is outside the allowed clock-skew window.");
    }
  }

  private void requirePlausibleAcceptanceTime(Invitation invitation, OffsetDateTime acceptedAt) {
    Duration skew = properties.acceptanceClockSkew();
    OffsetDateTime now = OffsetDateTime.now(clock);
    if (acceptedAt.isBefore(invitation.getCreatedAt().minus(skew))
        || acceptedAt.isAfter(now.plus(skew))) {
      throw ApiException.badRequest(
          "invitation_acceptance_time_invalid",
          "The product acceptance time is outside the allowed clock-skew window.");
    }
  }

  private void requireOwner(String owner, String caller) {
    if (!owner.equals(caller)) {
      throw ApiException.forbidden(
          "invitation_product_mismatch", "This invitation belongs to another product.");
    }
  }

  private void requireSameRequest(
      InvitationProjection invitation, String product, CreateInvitationInput input) {
    requireOwner(invitation.getProduct(), product);
    boolean same =
        invitation.getTenantId().equals(input.tenantId())
            && invitation.getTenantResourceType().equals(input.tenantResourceType())
            && invitation.getInvitedEmail().equals(EmailAddress.of(input.email()).value())
            && invitation.getInvitedBy().equals(input.invitedBy())
            && URI.create(invitation.getAcceptUrlBase()).equals(input.acceptUrlBase());
    if (!same) {
      throw ApiException.conflict(
          "invitation_request_conflict",
          "The invitation request identifier was already used with different data.");
    }
  }

  private String generateToken() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String invitationUrl(URI base, String token) {
    String value = base.toString();
    return (value.endsWith("/") ? value : value + "/") + token;
  }
}
