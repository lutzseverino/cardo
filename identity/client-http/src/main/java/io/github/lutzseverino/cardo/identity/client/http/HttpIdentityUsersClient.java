package io.github.lutzseverino.cardo.identity.client.http;

import io.github.lutzseverino.cardo.identity.client.IdentityOperation;
import io.github.lutzseverino.cardo.identity.client.IdentityOperationStatus;
import io.github.lutzseverino.cardo.identity.client.IdentityUser;
import io.github.lutzseverino.cardo.identity.client.IdentityUserStatus;
import io.github.lutzseverino.cardo.identity.client.IdentityUsersClient;
import io.github.lutzseverino.cardo.identity.client.ProvisionalUser;
import io.github.lutzseverino.cardo.identity.client.http.generated.CreateProvisionalUserRequest;
import io.github.lutzseverino.cardo.identity.client.http.generated.SearchUsersRequest;
import io.github.lutzseverino.cardo.identity.client.http.generated.UserResponse;
import io.github.lutzseverino.cardo.identity.client.http.generated.UserStatus;
import io.github.lutzseverino.cardo.identity.client.http.generated.api.UsersApi;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

final class HttpIdentityUsersClient implements IdentityUsersClient {

  private final UsersApi users;

  HttpIdentityUsersClient(UsersApi users) {
    this.users = users;
  }

  @Override
  public ProvisionalUser createProvisional(String email) {
    return toProvisionalUser(
        users.createProvisionalUser(new CreateProvisionalUserRequest().email(email)));
  }

  @Override
  public IdentityOperation requestCredentialSetup(
      UUID userId, UUID operationId, OffsetDateTime notAfter) {
    return toOperation(users.requestCredentialSetup(userId, operationId, notAfter));
  }

  @Override
  public IdentityOperation getCredentialSetup(UUID userId, UUID operationId) {
    return toOperation(users.getCredentialSetup(userId, operationId));
  }

  @Override
  public IdentityOperation cancelProvisional(UUID userId) {
    return toOperation(users.cancelProvisionalUser(userId));
  }

  @Override
  public IdentityOperation getProvisionalDeletion(UUID userId) {
    return toOperation(users.getProvisionalDeletion(userId));
  }

  @Override
  public List<IdentityUser> searchByAuthorizationSubjects(Collection<String> subjects) {
    return users
        .searchUsers(new SearchUsersRequest().authorizationSubjects(new LinkedHashSet<>(subjects)))
        .stream()
        .map(this::toIdentityUser)
        .toList();
  }

  @Override
  public IdentityUser get(UUID userId) {
    return toIdentityUser(users.getUser(userId));
  }

  private ProvisionalUser toProvisionalUser(UserResponse user) {
    return new ProvisionalUser(user.getId(), user.getAuthorizationSubject());
  }

  private IdentityUser toIdentityUser(UserResponse user) {
    return new IdentityUser(
        user.getId(),
        user.getAuthorizationSubject(),
        user.getEmail(),
        user.getName(),
        user.getAvatarUrl() == null ? null : user.getAvatarUrl().toString(),
        toStatus(user.getStatus()),
        user.getEmailVerified(),
        user.getCreatedAt(),
        user.getUpdatedAt());
  }

  private IdentityOperation toOperation(
      io.github.lutzseverino.cardo.identity.client.http.generated.IdentityOperationResponse
          operation) {
    return new IdentityOperation(
        operation.getId(),
        operation.getUserId(),
        IdentityOperationStatus.valueOf(operation.getStatus().name()),
        operation.getAttemptCount(),
        operation.getLastError(),
        operation.getExpiresAt(),
        operation.getCompletedAt(),
        operation.getCreatedAt(),
        operation.getUpdatedAt());
  }

  private IdentityUserStatus toStatus(UserStatus status) {
    return switch (status) {
      case INVITED -> IdentityUserStatus.INVITED;
      case ACTIVE -> IdentityUserStatus.ACTIVE;
      case DISABLED -> IdentityUserStatus.DISABLED;
    };
  }
}
