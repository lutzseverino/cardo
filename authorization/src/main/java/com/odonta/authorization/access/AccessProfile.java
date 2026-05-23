package com.odonta.authorization.access;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "authorization_access_profiles")
public class AccessProfile {

  @Id @GeneratedValue private UUID id;

  @Column(nullable = false)
  private String product;

  @Column(name = "tenant_id")
  private UUID tenantId;

  @Column(nullable = false)
  private String name;

  private String description;

  @Column(nullable = false)
  private boolean template;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected AccessProfile(
      String product, UUID tenantId, String name, String description, boolean template) {
    this.product = product;
    this.tenantId = tenantId;
    this.name = name;
    this.description = description;
    this.template = template;
  }

  @PrePersist
  void initializeTimestamps() {
    OffsetDateTime now = OffsetDateTime.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void touchUpdatedAt() {
    updatedAt = OffsetDateTime.now();
  }
}
