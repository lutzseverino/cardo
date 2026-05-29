package com.odonta.identity.service;

import com.odonta.authorization.sync.AuthorizationSyncService;
import com.odonta.common.api.ApiException;
import com.odonta.common.model.EmailAddress;
import com.odonta.identity.authorization.IdentityUserCreated;
import com.odonta.identity.model.CompleteProvisionalUserCommand;
import com.odonta.identity.model.CreateProvisionalUserCommand;
import com.odonta.identity.model.CreateUserCommand;
import com.odonta.identity.model.UpdateCurrentUserCommand;
import com.odonta.identity.model.UpdateUserCommand;
import com.odonta.identity.model.User;
import com.odonta.identity.model.UserProjection;
import com.odonta.identity.model.UserStatus;
import com.odonta.identity.provider.IdentityProvider;
import com.odonta.identity.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository users;
  private final IdentityProvider identityProvider;
  private final AuthorizationSyncService authorizationSync;

  @Transactional
  public UserProjection create(CreateUserCommand command) {
    UserProjection user;
    EmailAddress email = EmailAddress.of(command.email());
    try {
      IdentityProvider.ProvisionedIdentity identity =
          identityProvider.provisionPasswordIdentity(
              email.value(), command.password(), command.name());
      User created =
          users.saveAndFlush(new User(identity.subject(), email.value(), command.name()));
      identityProvider.bindUserId(identity.subject(), created.getId());
      authorizationSync.enqueue(new IdentityUserCreated(created));
      user = getProjection(created.getId());
    } catch (DataIntegrityViolationException exception) {
      throw ApiException.conflict("user_exists", "A user with this email already exists.");
    }

    return user;
  }

  @Transactional
  public UserProjection createProvisional(CreateProvisionalUserCommand command) {
    UserProjection user;
    EmailAddress email = EmailAddress.of(command.email());
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

    return user;
  }

  @Transactional
  public UserProjection completeProvisional(UUID id, CompleteProvisionalUserCommand command) {
    User user =
        users
            .findById(id)
            .orElseThrow(() -> ApiException.notFound("user_not_found", "User not found."));
    if (!UserStatus.INVITED.equals(user.getStatus())) {
      throw ApiException.conflict("user_already_complete", "User is already complete.");
    }

    identityProvider.completePasswordIdentity(
        user.getKeycloakSubject(), command.password(), command.name());
    user.complete(command.name());
    users.saveAndFlush(user);
    return getProjection(user.getId());
  }

  public UserProjection get(UUID id) {
    return getProjection(id);
  }

  public UserProjection getByEmail(String email) {
    return users
        .findProjectedByEmail(EmailAddress.of(email).value())
        .orElseThrow(() -> ApiException.notFound("user_not_found", "User not found."));
  }

  public UserProjection getCurrent(String keycloakSubject) {
    return getProjectionByKeycloakSubject(keycloakSubject);
  }

  public UserProjection update(UUID id, UpdateUserCommand command) {
    User user =
        users
            .findById(id)
            .orElseThrow(() -> ApiException.notFound("user_not_found", "User not found."));
    user.setName(command.name() == null ? user.getName() : command.name());
    user.setAvatarUrl(command.avatarUrl() == null ? user.getAvatarUrl() : command.avatarUrl());
    user.setStatus(command.status() == null ? user.getStatus() : command.status());
    users.saveAndFlush(user);
    return getProjection(id);
  }

  public UserProjection updateCurrent(String keycloakSubject, UpdateCurrentUserCommand command) {
    User user =
        users
            .findProjectedByKeycloakSubject(keycloakSubject)
            .map(UserProjection::getId)
            .flatMap(users::findById)
            .orElseThrow(() -> ApiException.notFound("user_not_found", "User not found."));
    user.setName(command.name() == null ? user.getName() : command.name());
    user.setAvatarUrl(command.avatarUrl() == null ? user.getAvatarUrl() : command.avatarUrl());
    users.saveAndFlush(user);
    return getProjection(user.getId());
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
