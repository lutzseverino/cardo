package com.odonta.identity.controller;

import com.odonta.identity.api.UsersApi;
import com.odonta.identity.api.model.CompleteProvisionalUserInput;
import com.odonta.identity.api.model.CreateProvisionalUserInput;
import com.odonta.identity.api.model.CreateUserInput;
import com.odonta.identity.api.model.SearchUsersRequest;
import com.odonta.identity.api.model.UpdateCurrentUserInput;
import com.odonta.identity.api.model.UpdateUserInput;
import com.odonta.identity.api.model.UserResponse;
import com.odonta.identity.api.model.UsersResponse;
import com.odonta.identity.mapper.UserMapper;
import com.odonta.identity.reader.CurrentJwtReader;
import com.odonta.identity.service.UserService;
import jakarta.validation.Valid;
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
  public ResponseEntity<UserResponse> createUser(@Valid CreateUserInput input) {
    return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(users.create(input)));
  }

  @Override
  public ResponseEntity<UserResponse> createProvisionalUser(
      @Valid CreateProvisionalUserInput input) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(mapper.toResponse(users.createProvisional(input)));
  }

  @Override
  public ResponseEntity<UserResponse> completeProvisionalUser(
      UUID userId, @Valid CompleteProvisionalUserInput input) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(mapper.toResponse(users.completeProvisional(userId, input)));
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
  public ResponseEntity<UserResponse> updateCurrentUser(@Valid UpdateCurrentUserInput input) {
    return ResponseEntity.ok(
        mapper.toResponse(users.updateCurrent(currentJwt.current().getName(), input)));
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
  public ResponseEntity<UserResponse> updateUser(UUID userId, @Valid UpdateUserInput input) {
    return ResponseEntity.ok(mapper.toResponse(users.update(userId, input)));
  }
}
