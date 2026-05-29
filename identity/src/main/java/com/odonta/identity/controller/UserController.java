package com.odonta.identity.controller;

import com.odonta.identity.IdentityPermissions;
import com.odonta.identity.api.UsersApi;
import com.odonta.identity.api.model.CompleteProvisionalUserRequest;
import com.odonta.identity.api.model.CreateProvisionalUserRequest;
import com.odonta.identity.api.model.CreateUserRequest;
import com.odonta.identity.api.model.UpdateCurrentUserRequest;
import com.odonta.identity.api.model.UpdateUserRequest;
import com.odonta.identity.api.model.UserResponse;
import com.odonta.identity.mapper.UserMapper;
import com.odonta.identity.model.CompleteProvisionalUserCommand;
import com.odonta.identity.model.CreateProvisionalUserCommand;
import com.odonta.identity.model.CreateUserCommand;
import com.odonta.identity.model.UpdateCurrentUserCommand;
import com.odonta.identity.model.UpdateUserCommand;
import com.odonta.identity.model.UserStatus;
import com.odonta.identity.reader.CurrentJwtReader;
import com.odonta.identity.service.UserService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${odonta.api.base-path}")
@RequiredArgsConstructor
public class UserController implements UsersApi {

  private final UserMapper mapper;
  private final CurrentJwtReader currentJwt;
  private final UserService users;

  @Override
  public ResponseEntity<UserResponse> createUser(@Valid CreateUserRequest request) {
    CreateUserCommand command =
        new CreateUserCommand(request.getEmail(), request.getPassword(), request.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(users.create(command)));
  }

  @Override
  @PreAuthorize("hasAuthority('" + IdentityPermissions.USER_PROVISION_AUTHORITY + "')")
  public ResponseEntity<UserResponse> createProvisionalUser(
      @Valid CreateProvisionalUserRequest request) {
    CreateProvisionalUserCommand command = new CreateProvisionalUserCommand(request.getEmail());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(mapper.toResponse(users.createProvisional(command)));
  }

  @Override
  @PreAuthorize("hasAuthority('" + IdentityPermissions.USER_PROVISION_AUTHORITY + "')")
  public ResponseEntity<UserResponse> completeProvisionalUser(
      UUID userId, @Valid CompleteProvisionalUserRequest request) {
    CompleteProvisionalUserCommand command =
        new CompleteProvisionalUserCommand(request.getName(), request.getPassword());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(mapper.toResponse(users.completeProvisional(userId, command)));
  }

  @Override
  @PreAuthorize("hasAuthority('" + IdentityPermissions.PROFILE_READ_AUTHORITY + "')")
  public ResponseEntity<UserResponse> getCurrentUser() {
    return ResponseEntity.ok(mapper.toResponse(users.getCurrent(currentJwt.current().getName())));
  }

  @Override
  @PreAuthorize("hasAuthority('" + IdentityPermissions.PROFILE_WRITE_AUTHORITY + "')")
  public ResponseEntity<UserResponse> updateCurrentUser(@Valid UpdateCurrentUserRequest request) {
    UpdateCurrentUserCommand command =
        new UpdateCurrentUserCommand(request.getName(), string(request.getAvatarUrl()));
    return ResponseEntity.ok(
        mapper.toResponse(users.updateCurrent(currentJwt.current().getName(), command)));
  }

  @Override
  @PreAuthorize(
      "hasPermission(#userId, '"
          + IdentityPermissions.USER_RESOURCE
          + "', '"
          + IdentityPermissions.READ
          + "')")
  public ResponseEntity<UserResponse> getUser(UUID userId) {
    return ResponseEntity.ok(mapper.toResponse(users.get(userId)));
  }

  @Override
  @PreAuthorize(
      "hasPermission('*', '"
          + IdentityPermissions.USER_RESOURCE
          + "', '"
          + IdentityPermissions.READ
          + "')")
  public ResponseEntity<UserResponse> getUserByEmail(String email) {
    return ResponseEntity.ok(mapper.toResponse(users.getByEmail(email)));
  }

  @Override
  @PreAuthorize(
      "hasPermission(#userId, '"
          + IdentityPermissions.USER_RESOURCE
          + "', '"
          + IdentityPermissions.WRITE
          + "')")
  public ResponseEntity<UserResponse> updateUser(UUID userId, @Valid UpdateUserRequest request) {
    UpdateUserCommand command =
        new UpdateUserCommand(
            request.getName(), string(request.getAvatarUrl()), status(request.getStatus()));
    return ResponseEntity.ok(mapper.toResponse(users.update(userId, command)));
  }

  private String string(URI value) {
    return value == null ? null : value.toString();
  }

  private UserStatus status(com.odonta.identity.api.model.UserStatus status) {
    return status == null ? null : UserStatus.fromWireValue(status.getValue());
  }
}
