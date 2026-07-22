package io.github.lutzseverino.cardo.invite.model;

import io.github.lutzseverino.cardo.common.data.AuditedEntity;
import io.github.lutzseverino.cardo.common.data.PersonalDataEntity;
import io.github.lutzseverino.cardo.common.model.EmailAddress;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "invitations",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_invitations_token", columnNames = "token"),
      @UniqueConstraint(
          name = "uk_invitations_product_request_id",
          columnNames = {"product", "request_id"})
    })
public class Invitation extends AuditedEntity implements PersonalDataEntity {

  @Id @GeneratedValue private UUID id;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "request_id", nullable = false)
  private UUID requestId;

  @Column(nullable = false)
  private String product;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "tenant_resource_type", nullable = false)
  private String tenantResourceType;

  @Column(name = "invited_email", nullable = false)
  private String invitedEmail;

  @Column(name = "invited_user_id", nullable = false)
  private UUID invitedUserId;

  @Column(name = "invited_by", nullable = false)
  private UUID invitedBy;

  @Column(name = "accept_url_base", nullable = false)
  private String acceptUrlBase;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @Column(nullable = false, unique = true)
  private String token;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private InvitationStatus status = InvitationStatus.PENDING;

  @Column(name = "accepted_at")
  private OffsetDateTime acceptedAt;

  @Column(name = "revoked_at")
  private OffsetDateTime revokedAt;

  public Invitation(
      UUID requestId,
      String product,
      UUID tenantId,
      String tenantResourceType,
      String invitedEmail,
      UUID invitedUserId,
      UUID invitedBy,
      URI acceptUrlBase,
      OffsetDateTime expiresAt,
      String token) {
    this.requestId = requestId;
    this.product = product;
    this.tenantId = tenantId;
    this.tenantResourceType = tenantResourceType;
    this.invitedEmail = invitedEmail;
    this.invitedUserId = invitedUserId;
    this.invitedBy = invitedBy;
    this.acceptUrlBase = acceptUrlBase.toString();
    this.expiresAt = expiresAt;
    this.token = token;
  }

  public boolean accept(OffsetDateTime acceptedAt) {
    if (InvitationStatus.ACCEPTED.equals(status)) {
      return false;
    }
    if (InvitationStatus.REVOKED.equals(status)) {
      throw io.github.lutzseverino.cardo.common.api.ApiException.conflict(
          "invitation_revoked", "A revoked invitation cannot be accepted.");
    }
    status = InvitationStatus.ACCEPTED;
    this.acceptedAt = acceptedAt;
    return true;
  }

  public boolean revoke(OffsetDateTime revokedAt) {
    if (InvitationStatus.REVOKED.equals(status)) {
      return false;
    }
    if (InvitationStatus.ACCEPTED.equals(status)) {
      throw io.github.lutzseverino.cardo.common.api.ApiException.conflict(
          "invitation_accepted", "An accepted invitation cannot be revoked.");
    }
    status = InvitationStatus.REVOKED;
    this.revokedAt = revokedAt;
    return true;
  }

  public URI acceptUrlBase() {
    return URI.create(acceptUrlBase);
  }

  @PrePersist
  void normalizeEmail() {
    invitedEmail = EmailAddress.of(invitedEmail).value();
  }
}
