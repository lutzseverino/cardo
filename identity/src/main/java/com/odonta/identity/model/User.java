package com.odonta.identity.model;

import com.odonta.authorization.resource.AuthorizationResourceType;
import com.odonta.authorization.resource.TargetableAuthorizationResource;
import com.odonta.common.data.AuditedEntity;
import com.odonta.common.data.PersonalDataEntity;
import com.odonta.common.model.EmailAddress;
import com.odonta.identity.IdentityResources;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "users",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
      @UniqueConstraint(name = "uk_users_keycloak_subject", columnNames = "keycloak_subject")
    })
public class User extends AuditedEntity
    implements TargetableAuthorizationResource, PersonalDataEntity {

  @Id @GeneratedValue private UUID id;

  @Column(name = "keycloak_subject", nullable = false, unique = true)
  private String keycloakSubject;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "email_verified", nullable = false)
  private boolean emailVerified;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Setter
  private UserStatus status = UserStatus.ACTIVE;

  @Column @Setter private String name;

  @Column(name = "avatar_url")
  @Setter
  private String avatarUrl;

  public User(String keycloakSubject, String email, String name) {
    this.keycloakSubject = keycloakSubject;
    this.email = email;
    this.name = name;
  }

  public static User invited(String keycloakSubject, String email) {
    User user = new User(keycloakSubject, email, null);
    user.status = UserStatus.INVITED;
    return user;
  }

  public void complete(String name) {
    this.name = name;
    this.status = UserStatus.ACTIVE;
  }

  public void changeOperationalStatus(UserStatus status) {
    this.status = status;
  }

  @PrePersist
  void normalizeEmail() {
    email = EmailAddress.of(email).value();
  }

  @Override
  public AuthorizationResourceType authorizationResourceType() {
    return IdentityResources.USER;
  }

  @Override
  public UUID authorizationResourceId() {
    return id;
  }

  @Override
  public String authorizationOwnerSubject() {
    return keycloakSubject;
  }
}
