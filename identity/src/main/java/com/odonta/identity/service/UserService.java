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
import jakarta.validation.Valid;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Validated
@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository users;
  private final IdentityProvider identityProvider;
  private final AuthorizationSyncService authorizationSync;

  @Transactional
  public UserProjection create(@Valid CreateUserCommand command) {
    EmailAddress email = EmailAddress.of(command.email());
    if (users.findProjectedByEmail(email.value()).isPresent()) {
      throw ApiException.conflict("user_exists", "A user with this email already exists.");
    }
    return createIdentityUser(
        () ->
            identityProvider.provisionPasswordIdentity(
                email.value(), command.password(), command.name()),
        identity -> new User(identity.subject(), email.value(), command.name()),
        () -> {
          throw ApiException.conflict("user_exists", "A user with this email already exists.");
        });
  }

  @Transactional
  public UserProjection createProvisional(@Valid CreateProvisionalUserCommand command) {
    EmailAddress email = EmailAddress.of(command.email());
    return users
        .findProjectedByEmail(email.value())
        .orElseGet(
            () ->
                createIdentityUser(
                    () -> identityProvider.provisionProvisionalIdentity(email.value()),
                    identity -> User.invited(identity.subject(), email.value()),
                    () ->
                        users
                            .findProjectedByEmail(email.value())
                            .orElseThrow(
                                () ->
                                    ApiException.conflict(
                                        "user_exists", "A user with this email exists."))));
  }

  @Transactional
  public UserProjection completeProvisional(
      UUID id, @Valid CompleteProvisionalUserCommand command) {
    User user =
        users
            .findById(id)
            .orElseThrow(() -> ApiException.notFound("user_not_found", "User not found."));
    if (!UserStatus.INVITED.equals(user.getStatus())) {
      throw ApiException.conflict("user_already_complete", "User is already complete.");
    }

    user.complete(command.name());
    users.saveAndFlush(user);
    identityProvider.completePasswordIdentity(
        user.getKeycloakSubject(), command.password(), command.name());
    return getProjection(user.getId());
  }

  @Transactional
  public void cancelProvisional(UUID id) {
    User user =
        users
            .findById(id)
            .orElseThrow(() -> ApiException.notFound("user_not_found", "User not found."));
    if (!UserStatus.INVITED.equals(user.getStatus())) {
      throw ApiException.conflict("user_already_complete", "User is already complete.");
    }

    String subject = user.getKeycloakSubject();
    users.delete(user);
    users.flush();
    identityProvider.deleteIdentity(subject);
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

  public UserProjection update(UUID id, @Valid UpdateUserCommand command) {
    User user =
        users
            .findById(id)
            .orElseThrow(() -> ApiException.notFound("user_not_found", "User not found."));
    user.setName(command.name() == null ? user.getName() : command.name());
    user.setAvatarUrl(command.avatarUrl() == null ? user.getAvatarUrl() : command.avatarUrl());
    if (command.status() != null) {
      if (!command.status().isOperational()) {
        throw ApiException.badRequest(
            "user_status_invalid", "Invited is not an operational user status.");
      }
      user.changeOperationalStatus(command.status());
    }
    users.saveAndFlush(user);
    return getProjection(id);
  }

  public UserProjection updateCurrent(
      String keycloakSubject, @Valid UpdateCurrentUserCommand command) {
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

  private UserProjection createIdentityUser(
      Supplier<IdentityProvider.ProvisionedIdentity> provision,
      Function<IdentityProvider.ProvisionedIdentity, User> factory,
      Supplier<UserProjection> duplicateUser) {
    IdentityProvider.ProvisionedIdentity identity = provision.get();
    try {
      User created = users.saveAndFlush(factory.apply(identity));
      identityProvider.bindUserId(identity.subject(), created.getId());
      authorizationSync.enqueue(new IdentityUserCreated(created));
      return getProjection(created.getId());
    } catch (DataIntegrityViolationException exception) {
      deleteIdentity(identity.subject(), exception);
      return duplicateUser.get();
    } catch (RuntimeException exception) {
      deleteIdentity(identity.subject(), exception);
      throw exception;
    }
  }

  private void deleteIdentity(String subject, RuntimeException original) {
    try {
      identityProvider.deleteIdentity(subject);
    } catch (RuntimeException compensationFailure) {
      original.addSuppressed(compensationFailure);
    }
  }
}
