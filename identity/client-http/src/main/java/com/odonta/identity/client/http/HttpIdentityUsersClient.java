package com.odonta.identity.client.http;

import com.odonta.identity.client.IdentityUser;
import com.odonta.identity.client.IdentityUserStatus;
import com.odonta.identity.client.IdentityUsersClient;
import com.odonta.identity.client.ProvisionalUser;
import com.odonta.identity.client.http.generated.CompleteProvisionalUserRequest;
import com.odonta.identity.client.http.generated.CreateProvisionalUserRequest;
import com.odonta.identity.client.http.generated.SearchUsersRequest;
import com.odonta.identity.client.http.generated.UserResponse;
import com.odonta.identity.client.http.generated.UserStatus;
import com.odonta.identity.client.http.generated.api.UsersApi;
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
  public ProvisionalUser completeProvisional(UUID userId, String name, String password) {
    return toProvisionalUser(
        users.completeProvisionalUser(
            userId, new CompleteProvisionalUserRequest().name(name).password(password)));
  }

  @Override
  public void cancelProvisional(UUID userId) {
    users.cancelProvisionalUser(userId);
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

  private IdentityUserStatus toStatus(UserStatus status) {
    return switch (status) {
      case INVITED -> IdentityUserStatus.INVITED;
      case ACTIVE -> IdentityUserStatus.ACTIVE;
      case DISABLED -> IdentityUserStatus.DISABLED;
    };
  }
}
