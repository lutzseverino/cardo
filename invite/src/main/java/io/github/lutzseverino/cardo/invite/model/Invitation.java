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
    uniqueConstraints = {@UniqueConstraint(name = "uk_invitations_token", columnNames = "token")})
public class Invitation extends AuditedEntity implements PersonalDataEntity {

  @Id @GeneratedValue private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "tenant_resource_type", nullable = false)
  private String tenantResourceType;

  @Column(name = "access_profile_id", nullable = false)
  private UUID accessProfileId;

  @Column(name = "invited_email", nullable = false)
  private String invitedEmail;

  @Column(name = "invited_user_id", nullable = false)
  private UUID invitedUserId;

  @Column(name = "invited_authorization_subject", nullable = false)
  private String invitedAuthorizationSubject;

  @Column(name = "invited_by", nullable = false)
  private UUID invitedBy;

  @Column(nullable = false, unique = true)
  private String token;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private InvitationStatus status = InvitationStatus.PENDING;

  @Column(name = "accepted_at")
  private OffsetDateTime acceptedAt;

  public Invitation(
      UUID tenantId,
      String tenantResourceType,
      UUID accessProfileId,
      String invitedEmail,
      UUID invitedUserId,
      String invitedAuthorizationSubject,
      UUID invitedBy,
      String token) {
    this.tenantId = tenantId;
    this.tenantResourceType = tenantResourceType;
    this.accessProfileId = accessProfileId;
    this.invitedEmail = invitedEmail;
    this.invitedUserId = invitedUserId;
    this.invitedAuthorizationSubject = invitedAuthorizationSubject;
    this.invitedBy = invitedBy;
    this.token = token;
  }

  public void accept(OffsetDateTime acceptedAt) {
    status = InvitationStatus.ACCEPTED;
    this.acceptedAt = acceptedAt;
  }

  @PrePersist
  void normalizeEmail() {
    invitedEmail = EmailAddress.of(invitedEmail).value();
  }
}
