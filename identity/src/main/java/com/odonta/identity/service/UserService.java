package com.odonta.identity.service;

import com.odonta.authorization.sync.AuthorizationSyncService;
import com.odonta.common.api.ApiException;
import com.odonta.common.model.EmailAddress;
import com.odonta.identity.authorization.IdentityUserCreated;
import com.odonta.identity.mapper.UserMapper;
import com.odonta.identity.model.CompleteProvisionalUserRequest;
import com.odonta.identity.model.CreateProvisionalUserRequest;
import com.odonta.identity.model.CreateUserRequest;
import com.odonta.identity.model.UpdateCurrentUserRequest;
import com.odonta.identity.model.UpdateUserRequest;
import com.odonta.identity.model.User;
import com.odonta.identity.model.UserProjection;
import com.odonta.identity.model.UserResponse;
import com.odonta.identity.model.UserStatus;
import com.odonta.identity.provider.IdentityProvider;
import com.odonta.identity.repository.UserRepository;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private final UserRepository users;
  private final IdentityProvider identityProvider;
  private final AuthorizationSyncService authorizationSync;
  private final UserMapper mapper;

  UserService(
      UserRepository users,
      IdentityProvider identityProvider,
      AuthorizationSyncService authorizationSync,
      UserMapper mapper) {
    this.users = users;
    this.identityProvider = identityProvider;
    this.authorizationSync = authorizationSync;
    this.mapper = mapper;
  }

  @Transactional
  public UserResponse create(CreateUserRequest request) {
    UserProjection user;
    EmailAddress email = EmailAddress.of(request.email());
    try {
      IdentityProvider.ProvisionedIdentity identity =
          identityProvider.provisionPasswordIdentity(
              email.value(), request.password(), request.name());
      User created =
          users.saveAndFlush(new User(identity.subject(), email.value(), request.name()));
      identityProvider.bindUserId(identity.subject(), created.getId());
      authorizationSync.enqueue(new IdentityUserCreated(created));
      user = getProjection(created.getId());
    } catch (DataIntegrityViolationException exception) {
      throw ApiException.conflict("user_exists", "A user with this email already exists.");
    }

    return mapper.toResponse(user);
  }

  @Transactional
  public UserResponse createProvisional(CreateProvisionalUserRequest request) {
    UserProjection user;
    EmailAddress email = EmailAddress.of(request.email());
    try {
      IdentityProvider.ProvisionedIdentity identity =
          identityProvider.provisionProvisionalIdentity(email.value());
      User created = users.saveAndFlush(User.invited(identity.subject(), email.value()));
      identityProvider.bindUserId(identity.subject(), created.getId());
      authorizationSync.enqueue(new IdentityUserCreated(created));
      user = getProjection(created.getId());
    } catch (DataIntegrityViolationException exception) {
      user =
          users
              .findProjectedByEmail(email.value())
              .orElseThrow(
                  () -> ApiException.conflict("user_exists", "A user with this email exists."));
    }

    return mapper.toResponse(user);
  }

  @Transactional
  public UserResponse completeProvisional(UUID id, CompleteProvisionalUserRequest request) {
    User user =
        users
            .findById(id)
            .orElseThrow(() -> ApiException.notFound("user_not_found", "User not found."));
    if (!UserStatus.INVITED.equals(user.getStatus())) {
      throw ApiException.conflict("user_already_complete", "User is already complete.");
    }

    identityProvider.completePasswordIdentity(
        user.getKeycloakSubject(), request.password(), request.name());
    user.complete(request.name());
    users.saveAndFlush(user);
    return mapper.toResponse(getProjection(user.getId()));
  }

  public UserResponse get(UUID id) {
    return mapper.toResponse(getProjection(id));
  }

  public UserResponse getByEmail(String email) {
    return mapper.toResponse(
        users
            .findProjectedByEmail(EmailAddress.of(email).value())
            .orElseThrow(() -> ApiException.notFound("user_not_found", "User not found.")));
  }

  public UserResponse getCurrent(String keycloakSubject) {
    return mapper.toResponse(getProjectionByKeycloakSubject(keycloakSubject));
  }

  public UserResponse update(UUID id, UpdateUserRequest request) {
    User user =
        users
            .findById(id)
            .orElseThrow(() -> ApiException.notFound("user_not_found", "User not found."));
    user.setName(request.name() == null ? user.getName() : request.name());
    user.setAvatarUrl(request.avatarUrl() == null ? user.getAvatarUrl() : request.avatarUrl());
    user.setStatus(request.status() == null ? user.getStatus() : request.status());
    users.saveAndFlush(user);
    return mapper.toResponse(getProjection(id));
  }

  public UserResponse updateCurrent(String keycloakSubject, UpdateCurrentUserRequest request) {
    User user =
        users
            .findProjectedByKeycloakSubject(keycloakSubject)
            .map(UserProjection::getId)
            .flatMap(users::findById)
            .orElseThrow(() -> ApiException.notFound("user_not_found", "User not found."));
    user.setName(request.name() == null ? user.getName() : request.name());
    user.setAvatarUrl(request.avatarUrl() == null ? user.getAvatarUrl() : request.avatarUrl());
    users.saveAndFlush(user);
    return mapper.toResponse(getProjection(user.getId()));
  }

  private UserProjection getProjection(UUID id) {
    return users
        .findProjectedById(id)
        .orElseThrow(() -> ApiException.notFound("user_not_found", "User not found."));
  }

  private UserProjection getProjectionByKeycloakSubject(String keycloakSubject) {
    return users
        .findProjectedByKeycloakSubject(keycloakSubject)
        .orElseThrow(() -> ApiException.notFound("user_not_found", "User not found."));
  }
}
