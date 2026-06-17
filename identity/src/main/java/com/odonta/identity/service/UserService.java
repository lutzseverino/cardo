package com.odonta.identity.service;

import com.odonta.authorization.grant.Grants;
import com.odonta.common.api.ApiException;
import com.odonta.common.model.EmailAddress;
import com.odonta.identity.IdentityPermissions;
import com.odonta.identity.authorization.IdentityGrantPlanner;
import com.odonta.identity.mapper.UserApplicationMapper;
import com.odonta.identity.model.CompleteProvisionalUserInput;
import com.odonta.identity.model.CreateProvisionalUserInput;
import com.odonta.identity.model.CreateUserInput;
import com.odonta.identity.model.UpdateCurrentUserInput;
import com.odonta.identity.model.UpdateUserInput;
import com.odonta.identity.model.User;
import com.odonta.identity.model.UserResult;
import com.odonta.identity.model.UserStatus;
import com.odonta.identity.provider.IdentityProvider;
import com.odonta.identity.repository.UserProjection;
import com.odonta.identity.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
  private final UserApplicationMapper mapper;
  private final IdentityProvider identityProvider;
  private final Grants grants;
  private final IdentityGrantPlanner identityGrantPlanner;

  @Transactional
  public UserResult create(@Valid CreateUserInput input) {
    EmailAddress email = EmailAddress.of(input.email());
    if (users.findProjectedByEmail(email.value()).isPresent()) {
      throw ApiException.conflict("user_exists", "A user with this email already exists.");
    }
    return createIdentityUser(
        () ->
            identityProvider.provisionPasswordIdentity(
                email.value(), input.password(), input.name()),
        identity -> new User(identity.subject(), email.value(), input.name()),
        () -> {
          throw ApiException.conflict("user_exists", "A user with this email already exists.");
        });
  }

  @Transactional
  @PreAuthorize("hasAuthority('" + IdentityPermissions.USER_PROVISION_AUTHORITY + "')")
  public UserResult createProvisional(@Valid CreateProvisionalUserInput input) {
    EmailAddress email = EmailAddress.of(input.email());
    return users
        .findProjectedByEmail(email.value())
        .map(mapper::toResult)
        .orElseGet(
            () ->
                createIdentityUser(
                    () -> identityProvider.provisionProvisionalIdentity(email.value()),
                    identity -> User.invited(identity.subject(), email.value()),
                    () ->
                        users
                            .findProjectedByEmail(email.value())
                            .map(mapper::toResult)
                            .orElseThrow(
                                () ->
                                    ApiException.conflict(
                                        "user_exists", "A user with this email exists."))));
  }

  @Transactional
  @PreAuthorize("hasAuthority('" + IdentityPermissions.USER_PROVISION_AUTHORITY + "')")
  public UserResult completeProvisional(UUID id, @Valid CompleteProvisionalUserInput input) {
    User user =
        users
            .findById(id)
            .orElseThrow(() -> ApiException.notFound("user_not_found", "User not found."));
    if (!UserStatus.INVITED.equals(user.getStatus())) {
      throw ApiException.conflict("user_already_complete", "User is already complete.");
    }

    user.complete(input.name());
    users.saveAndFlush(user);
    identityProvider.completePasswordIdentity(
        user.getKeycloakSubject(), input.password(), input.name());
    return getResult(user.getId());
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
  public UserResult get(UUID id) {
    return getResult(id);
  }

  @PreAuthorize(
      "hasPermission('*', '"
          + IdentityPermissions.USER_RESOURCE
          + "', '"
          + IdentityPermissions.READ
          + "')")
  public UserResult getByEmail(@NotBlank @Email String email) {
    return users
        .findProjectedByEmail(EmailAddress.of(email).value())
        .map(mapper::toResult)
        .orElseThrow(() -> ApiException.notFound("user_not_found", "User not found."));
  }

  @PreAuthorize("hasAuthority('" + IdentityPermissions.PROFILE_READ_AUTHORITY + "')")
  public UserResult getCurrent(String keycloakSubject) {
    return mapper.toResult(getProjectionByKeycloakSubject(keycloakSubject));
  }

  @PreAuthorize(
      "hasPermission('*', '"
          + IdentityPermissions.USER_RESOURCE
          + "', '"
          + IdentityPermissions.READ
          + "')")
  public List<UserResult> searchByAuthorizationSubjects(Collection<String> authorizationSubjects) {
    List<String> subjects = normalizedAuthorizationSubjects(authorizationSubjects);
    if (subjects.isEmpty()) {
      return List.of();
    }
    return users.findProjectedByKeycloakSubjectIn(subjects).stream().map(mapper::toResult).toList();
  }

  @Transactional
  @PreAuthorize(
      "hasPermission(#id, '"
          + IdentityPermissions.USER_RESOURCE
          + "', '"
          + IdentityPermissions.WRITE
          + "')")
  public UserResult update(UUID id, @Valid UpdateUserInput input) {
    User user =
        users
            .findById(id)
            .orElseThrow(() -> ApiException.notFound("user_not_found", "User not found."));
    user.setName(input.name() == null ? user.getName() : input.name());
    if (input.avatarUrl().present()) {
      user.setAvatarUrl(input.avatarUrl().value());
    }
    if (input.status() != null) {
      if (!input.status().isOperational()) {
        throw ApiException.badRequest(
            "user_status_invalid", "Invited is not an operational user status.");
      }
      user.changeOperationalStatus(input.status());
    }
    users.saveAndFlush(user);
    return getResult(id);
  }

  @Transactional
  @PreAuthorize("hasAuthority('" + IdentityPermissions.PROFILE_WRITE_AUTHORITY + "')")
  public UserResult updateCurrent(String keycloakSubject, @Valid UpdateCurrentUserInput input) {
    User user =
        users
            .findProjectedByKeycloakSubject(keycloakSubject)
            .map(UserProjection::getId)
            .flatMap(users::findById)
            .orElseThrow(() -> ApiException.notFound("user_not_found", "User not found."));
    user.setName(input.name() == null ? user.getName() : input.name());
    if (input.avatarUrl().present()) {
      user.setAvatarUrl(input.avatarUrl().value());
    }
    users.saveAndFlush(user);
    return getResult(user.getId());
  }

  private UserResult getResult(UUID id) {
    return mapper.toResult(getProjection(id));
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

  private UserResult createIdentityUser(
      Supplier<IdentityProvider.ProvisionedIdentity> provision,
      Function<IdentityProvider.ProvisionedIdentity, User> factory,
      Supplier<UserResult> duplicateUser) {
    IdentityProvider.ProvisionedIdentity identity = provision.get();
    try {
      User created = users.saveAndFlush(factory.apply(identity));
      identityProvider.bindUserId(identity.subject(), created.getId());
      grants.stage(identityGrantPlanner.creation(created));
      return getResult(created.getId());
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
