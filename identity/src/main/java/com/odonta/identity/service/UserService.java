package com.odonta.identity.service;

import com.odonta.authorization.grant.Grants;
import com.odonta.common.api.ApiException;
import com.odonta.common.model.EmailAddress;
import com.odonta.identity.IdentityPermissions;
import com.odonta.identity.api.model.CompleteProvisionalUserInput;
import com.odonta.identity.api.model.CreateProvisionalUserInput;
import com.odonta.identity.api.model.CreateUserInput;
import com.odonta.identity.api.model.UpdateCurrentUserInput;
import com.odonta.identity.api.model.UpdateUserInput;
import com.odonta.identity.authorization.IdentityGrantPlanner;
import com.odonta.identity.model.User;
import com.odonta.identity.model.UserProjection;
import com.odonta.identity.model.UserStatus;
import com.odonta.identity.provider.IdentityProvider;
import com.odonta.identity.repository.UserRepository;
import jakarta.validation.Valid;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Validated
@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository users;
  private final IdentityProvider identityProvider;
  private final Grants grants;
  private final IdentityGrantPlanner identityGrantPlanner;

  @Transactional
  public UserProjection create(@Valid CreateUserInput input) {
    EmailAddress email = EmailAddress.of(input.getEmail());
    if (users.findProjectedByEmail(email.value()).isPresent()) {
      throw ApiException.conflict("user_exists", "A user with this email already exists.");
    }
    return createIdentityUser(
        () ->
            identityProvider.provisionPasswordIdentity(
                email.value(), input.getPassword(), input.getName()),
        identity -> new User(identity.subject(), email.value(), input.getName()),
        () -> {
          throw ApiException.conflict("user_exists", "A user with this email already exists.");
        });
  }

  @Transactional
  @PreAuthorize("hasAuthority('" + IdentityPermissions.USER_PROVISION_AUTHORITY + "')")
  public UserProjection createProvisional(@Valid CreateProvisionalUserInput input) {
    EmailAddress email = EmailAddress.of(input.getEmail());
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
  @PreAuthorize("hasAuthority('" + IdentityPermissions.USER_PROVISION_AUTHORITY + "')")
  public UserProjection completeProvisional(UUID id, @Valid CompleteProvisionalUserInput input) {
    User user =
        users
            .findById(id)
            .orElseThrow(() -> ApiException.notFound("user_not_found", "User not found."));
    if (!UserStatus.INVITED.equals(user.getStatus())) {
      throw ApiException.conflict("user_already_complete", "User is already complete.");
    }

    user.complete(input.getName());
    users.saveAndFlush(user);
    identityProvider.completePasswordIdentity(
        user.getKeycloakSubject(), input.getPassword(), input.getName());
    return getProjection(user.getId());
  }

  @Transactional
  @PreAuthorize("hasAuthority('" + IdentityPermissions.USER_PROVISION_AUTHORITY + "')")
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

  @PreAuthorize(
      "hasPermission(#id, '"
          + IdentityPermissions.USER_RESOURCE
          + "', '"
          + IdentityPermissions.READ
          + "')")
  public UserProjection get(UUID id) {
    return getProjection(id);
  }

  @PreAuthorize(
      "hasPermission('*', '"
          + IdentityPermissions.USER_RESOURCE
          + "', '"
          + IdentityPermissions.READ
          + "')")
  public UserProjection getByEmail(String email) {
    return users
        .findProjectedByEmail(EmailAddress.of(email).value())
        .orElseThrow(() -> ApiException.notFound("user_not_found", "User not found."));
  }

  @PreAuthorize("hasAuthority('" + IdentityPermissions.PROFILE_READ_AUTHORITY + "')")
  public UserProjection getCurrent(String keycloakSubject) {
    return getProjectionByKeycloakSubject(keycloakSubject);
  }

  @PreAuthorize(
      "hasPermission('*', '"
          + IdentityPermissions.USER_RESOURCE
          + "', '"
          + IdentityPermissions.READ
          + "')")
  public List<UserProjection> searchByAuthorizationSubjects(
      Collection<String> authorizationSubjects) {
    List<String> subjects = normalizedAuthorizationSubjects(authorizationSubjects);
    if (subjects.isEmpty()) {
      return List.of();
    }
    return users.findProjectedByKeycloakSubjectIn(subjects);
  }

  @Transactional
  @PreAuthorize(
      "hasPermission(#id, '"
          + IdentityPermissions.USER_RESOURCE
          + "', '"
          + IdentityPermissions.WRITE
          + "')")
  public UserProjection update(UUID id, @Valid UpdateUserInput input) {
    User user =
        users
            .findById(id)
            .orElseThrow(() -> ApiException.notFound("user_not_found", "User not found."));
    user.setName(input.getName() == null ? user.getName() : input.getName());
    user.setAvatarUrl(
        input.getAvatarUrl() == null ? user.getAvatarUrl() : input.getAvatarUrl().toString());
    if (input.getStatus() != null) {
      if (!input.getStatus().isOperational()) {
        throw ApiException.badRequest(
            "user_status_invalid", "Invited is not an operational user status.");
      }
      user.changeOperationalStatus(input.getStatus());
    }
    users.saveAndFlush(user);
    return getProjection(id);
  }

  @Transactional
  @PreAuthorize("hasAuthority('" + IdentityPermissions.PROFILE_WRITE_AUTHORITY + "')")
  public UserProjection updateCurrent(String keycloakSubject, @Valid UpdateCurrentUserInput input) {
    User user =
        users
            .findProjectedByKeycloakSubject(keycloakSubject)
            .map(UserProjection::getId)
            .flatMap(users::findById)
            .orElseThrow(() -> ApiException.notFound("user_not_found", "User not found."));
    user.setName(input.getName() == null ? user.getName() : input.getName());
    user.setAvatarUrl(
        input.getAvatarUrl() == null ? user.getAvatarUrl() : input.getAvatarUrl().toString());
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

  private List<String> normalizedAuthorizationSubjects(Collection<String> authorizationSubjects) {
    return authorizationSubjects == null
        ? List.of()
        : new LinkedHashSet<>(authorizationSubjects)
            .stream().filter(Objects::nonNull).filter(subject -> !subject.isBlank()).toList();
  }

  private UserProjection createIdentityUser(
      Supplier<IdentityProvider.ProvisionedIdentity> provision,
      Function<IdentityProvider.ProvisionedIdentity, User> factory,
      Supplier<UserProjection> duplicateUser) {
    IdentityProvider.ProvisionedIdentity identity = provision.get();
    try {
      User created = users.saveAndFlush(factory.apply(identity));
      identityProvider.bindUserId(identity.subject(), created.getId());
      grants.stage(identityGrantPlanner.creation(created));
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
