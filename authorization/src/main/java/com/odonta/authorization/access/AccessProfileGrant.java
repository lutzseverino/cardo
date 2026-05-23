package com.odonta.authorization.access;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "authorization_access_profile_grants")
public class AccessProfileGrant {

  @Id @GeneratedValue private UUID id;

  @Column(name = "profile_id", nullable = false)
  private UUID profileId;

  @Column(name = "resource_type", nullable = false)
  private String resourceType;

  @Column(name = "resource_id")
  private UUID resourceId;

  @Column(nullable = false)
  private String action;

  protected AccessProfileGrant(
      UUID profileId, String resourceType, UUID resourceId, String action) {
    this.profileId = profileId;
    this.resourceType = resourceType;
    this.resourceId = resourceId;
    this.action = action;
  }
}
