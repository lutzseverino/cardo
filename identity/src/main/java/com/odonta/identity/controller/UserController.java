package com.odonta.identity.controller;

import com.odonta.identity.api.UsersApi;
import com.odonta.identity.api.model.CompleteProvisionalUserRequest;
import com.odonta.identity.api.model.CreateProvisionalUserRequest;
import com.odonta.identity.api.model.CreateUserRequest;
import com.odonta.identity.api.model.SearchUsersRequest;
import com.odonta.identity.api.model.UpdateCurrentUserRequest;
import com.odonta.identity.api.model.UpdateUserRequest;
import com.odonta.identity.api.model.UpdateUserStatus;
import com.odonta.identity.api.model.UserResponse;
import com.odonta.identity.api.model.UsersResponse;
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
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
  public ResponseEntity<UserResponse> createProvisionalUser(
      @Valid CreateProvisionalUserRequest request) {
    CreateProvisionalUserCommand command = new CreateProvisionalUserCommand(request.getEmail());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(mapper.toResponse(users.createProvisional(command)));
  }

  @Override
  public ResponseEntity<UserResponse> completeProvisionalUser(
      UUID userId, @Valid CompleteProvisionalUserRequest request) {
    CompleteProvisionalUserCommand command =
        new CompleteProvisionalUserCommand(request.getName(), request.getPassword());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(mapper.toResponse(users.completeProvisional(userId, command)));
  }

  @Override
  public ResponseEntity<Void> cancelProvisionalUser(UUID userId) {
    users.cancelProvisional(userId);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<UserResponse> getCurrentUser() {
    return ResponseEntity.ok(mapper.toResponse(users.getCurrent(currentJwt.current().getName())));
  }

  @Override
  public ResponseEntity<UserResponse> updateCurrentUser(@Valid UpdateCurrentUserRequest request) {
    UpdateCurrentUserCommand command =
        new UpdateCurrentUserCommand(request.getName(), string(request.getAvatarUrl()));
    return ResponseEntity.ok(
        mapper.toResponse(users.updateCurrent(currentJwt.current().getName(), command)));
  }

  @Override
  public ResponseEntity<UserResponse> getUser(UUID userId) {
    return ResponseEntity.ok(mapper.toResponse(users.get(userId)));
  }

  @Override
  public ResponseEntity<UserResponse> getUserByEmail(String email) {
    return ResponseEntity.ok(mapper.toResponse(users.getByEmail(email)));
  }

  @Override
  public ResponseEntity<UsersResponse> searchUsers(@Valid SearchUsersRequest request) {
    return ResponseEntity.ok(
        new UsersResponse(
            mapper.toResponses(
                users.searchByAuthorizationSubjects(
                    request.getAuthorizationSubjects() == null
                        ? List.of()
                        : request.getAuthorizationSubjects().stream().toList()))));
  }

  @Override
  public ResponseEntity<UserResponse> updateUser(UUID userId, @Valid UpdateUserRequest request) {
    UpdateUserCommand command =
        new UpdateUserCommand(
            request.getName(), string(request.getAvatarUrl()), status(request.getStatus()));
    return ResponseEntity.ok(mapper.toResponse(users.update(userId, command)));
  }

  private String string(URI value) {
    return value == null ? null : value.toString();
  }

  private UserStatus status(UpdateUserStatus status) {
    return status == null ? null : UserStatus.fromWireValue(status.getValue());
  }
}
