package io.github.lutzseverino.cardo.identity.service;

import io.github.lutzseverino.cardo.authorization.grant.Grants;
import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.common.model.EmailAddress;
import io.github.lutzseverino.cardo.identity.IdentityPermissions;
import io.github.lutzseverino.cardo.identity.authorization.IdentityGrantPlanner;
import io.github.lutzseverino.cardo.identity.mapper.UserApplicationMapper;
import io.github.lutzseverino.cardo.identity.model.CreateProvisionalUserInput;
import io.github.lutzseverino.cardo.identity.model.CreateUserInput;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutationTerminalReason;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutationWork;
import io.github.lutzseverino.cardo.identity.model.PasswordProvisioningIntent;
import io.github.lutzseverino.cardo.identity.model.UpdateCurrentUserInput;
import io.github.lutzseverino.cardo.identity.model.UpdateUserInput;
import io.github.lutzseverino.cardo.identity.model.User;
import io.github.lutzseverino.cardo.identity.model.UserResult;
import io.github.lutzseverino.cardo.identity.model.UserStatus;
import io.github.lutzseverino.cardo.identity.provider.IdentityProvider;
import io.github.lutzseverino.cardo.identity.repository.UserProjection;
import io.github.lutzseverino.cardo.identity.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;

@Validated
@Service
public class UserService {

  private static final Logger logger = LoggerFactory.getLogger(UserService.class);

  private final UserRepository users;
  private final UserApplicationMapper mapper;
  private final IdentityProvider identityProvider;
  private final IdentityProviderMutationService providerMutations;
  private final Grants grants;
  private final IdentityGrantPlanner identityGrantPlanner;
  private final TransactionOperations transactions;

  public UserService(
      UserRepository users,
      UserApplicationMapper mapper,
      IdentityProvider identityProvider,
      IdentityProviderMutationService providerMutations,
      Grants grants,
      IdentityGrantPlanner identityGrantPlanner,
      PlatformTransactionManager transactionManager) {
    this(
        users,
        mapper,
        identityProvider,
        providerMutations,
        grants,
        identityGrantPlanner,
        new TransactionTemplate(transactionManager));
  }

  UserService(
      UserRepository users,
      UserApplicationMapper mapper,
      IdentityProvider identityProvider,
      IdentityProviderMutationService providerMutations,
      Grants grants,
      IdentityGrantPlanner identityGrantPlanner,
      TransactionOperations transactions) {
    this.users = users;
    this.mapper = mapper;
    this.identityProvider = identityProvider;
    this.providerMutations = providerMutations;
    this.grants = grants;
    this.identityGrantPlanner = identityGrantPlanner;
    this.transactions = transactions;
  }

  public UserResult create(@Valid CreateUserInput input) {
    EmailAddress email = EmailAddress.of(input.email());
    if (users.findProjectedByEmail(email.value()).isPresent()) {
      throw ApiException.conflict("user_exists", "A user with this email already exists.");
    }
    PasswordProvisioningIntent intent =
        providerMutations.requestPasswordProvision(email.value(), input.name());
    IdentityProvider.ProvisionedIdentity identity =
        provisionPasswordIdentity(intent, input.password());
    return transactions.execute(status -> completePasswordProvision(intent, identity.subject()));
  }

  @Transactional
  public UserResult recoverPasswordProvision(
      IdentityProviderMutationWork work, String providerSubject) {
    User user =
        users
            .findProjectedByEmail(work.email())
            .map(
                existing -> {
                  if (!providerSubject.equals(existing.getKeycloakSubject())) {
                    throw ApiException.conflict(
                        "user_provisioning_conflict",
                        "Provisioned identity conflicts with an existing local user.");
                  }
                  return users
                      .findById(existing.getId())
                      .orElseThrow(
                          () -> ApiException.notFound("user_not_found", "User not found."));
                })
            .orElseGet(
                () -> users.saveAndFlush(new User(providerSubject, work.email(), work.name())));
    providerMutations.completeRecoveredPasswordProvision(work, providerSubject, user.getId());
    grants.stage(identityGrantPlanner.creation(user));
    return getResult(user.getId());
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
            .findEntityByIdForUpdate(id)
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
    if (input.status() != null) {
      providerMutations.requestEnabledState(
          id, user.getKeycloakSubject(), UserStatus.ACTIVE.equals(input.status()));
    }
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
    AtomicBoolean compensated = deleteIdentityOnRollback(identity.subject());
    try {
      User created = users.saveAndFlush(factory.apply(identity));
      identityProvider.bindUserId(identity.subject(), created.getId());
      grants.stage(identityGrantPlanner.creation(created));
      return getResult(created.getId());
    } catch (DataIntegrityViolationException exception) {
      deleteIdentity(identity.subject(), compensated, exception);
      return duplicateUser.get();
    } catch (RuntimeException exception) {
      deleteIdentity(identity.subject(), compensated, exception);
      throw exception;
    }
  }

  private IdentityProvider.ProvisionedIdentity provisionPasswordIdentity(
      PasswordProvisioningIntent intent, String password) {
    try {
      return identityProvider.provisionPasswordIdentity(
          intent.email(), password, intent.name(), intent.correlationMarker());
    } catch (RuntimeException dispatchFailure) {
      try {
        Optional<IdentityProvider.ProvisionedIdentity> recovered =
            identityProvider.findPasswordIdentityByCorrelationMarker(intent.correlationMarker());
        if (recovered.isPresent()) {
          return recovered.orElseThrow();
        }
      } catch (RuntimeException recoveryFailure) {
        dispatchFailure.addSuppressed(recoveryFailure);
      }
      switch (passwordProvisioningFailure(dispatchFailure)) {
        case RECOVERABLE ->
            providerMutations.recordPasswordDispatchFailure(intent, dispatchFailure);
        case CREDENTIAL_REJECTED ->
            providerMutations.recordPasswordDispatchRejection(
                intent,
                dispatchFailure,
                IdentityProviderMutationTerminalReason.CREDENTIAL_RESUBMISSION_REQUIRED);
        case PROVIDER_REJECTED ->
            providerMutations.recordPasswordDispatchRejection(
                intent, dispatchFailure, IdentityProviderMutationTerminalReason.PROVIDER_REJECTED);
      }
      throw dispatchFailure;
    }
  }

  private UserResult completePasswordProvision(
      PasswordProvisioningIntent intent, String providerSubject) {
    if (users.findProjectedByEmail(intent.email()).isPresent()) {
      throw ApiException.conflict("user_exists", "A user with this email already exists.");
    }
    User user = users.saveAndFlush(new User(providerSubject, intent.email(), intent.name()));
    providerMutations.completePasswordProvision(intent, providerSubject, user.getId());
    grants.stage(identityGrantPlanner.creation(user));
    return getResult(user.getId());
  }

  private PasswordProvisioningFailure passwordProvisioningFailure(RuntimeException failure) {
    if (!(failure instanceof ApiException api)) {
      return PasswordProvisioningFailure.RECOVERABLE;
    }
    int status = api.status();
    if (status == 409) {
      return PasswordProvisioningFailure.RECOVERABLE;
    }
    if (status == 400 || status == 422) {
      return PasswordProvisioningFailure.CREDENTIAL_REJECTED;
    }
    if (status >= 400 && status < 500 && status != 408 && status != 425 && status != 429) {
      return PasswordProvisioningFailure.PROVIDER_REJECTED;
    }
    return PasswordProvisioningFailure.RECOVERABLE;
  }

  private enum PasswordProvisioningFailure {
    RECOVERABLE,
    CREDENTIAL_REJECTED,
    PROVIDER_REJECTED
  }

  private AtomicBoolean deleteIdentityOnRollback(String subject) {
    AtomicBoolean compensated = new AtomicBoolean();
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      return compensated;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED && compensated.compareAndSet(false, true)) {
              try {
                identityProvider.deleteIdentity(subject);
              } catch (RuntimeException exception) {
                logger.error(
                    "Failed to delete provisioned identity {} after rollback", subject, exception);
              }
            }
          }
        });
    return compensated;
  }

  private void deleteIdentity(
      String subject, AtomicBoolean compensated, RuntimeException original) {
    if (!compensated.compareAndSet(false, true)) {
      return;
    }
    try {
      identityProvider.deleteIdentity(subject);
    } catch (RuntimeException compensationFailure) {
      original.addSuppressed(compensationFailure);
    }
  }
}
