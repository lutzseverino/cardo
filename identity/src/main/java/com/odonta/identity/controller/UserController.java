package com.odonta.identity.controller;

import com.odonta.identity.api.UsersApi;
import com.odonta.identity.api.model.CompleteProvisionalUserRequest;
import com.odonta.identity.api.model.CreateProvisionalUserRequest;
import com.odonta.identity.api.model.CreateUserRequest;
import com.odonta.identity.api.model.SearchUsersRequest;
import com.odonta.identity.api.model.UpdateCurrentUserRequest;
import com.odonta.identity.api.model.UpdateUserRequest;
import com.odonta.identity.api.model.UserResponse;
import com.odonta.identity.mapper.UserTransportMapper;
import com.odonta.identity.patch.UserPatchAdapter;
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

  private final UserTransportMapper mapper;
  private final UserPatchAdapter patchAdapter;
  private final CurrentJwtReader currentJwt;
  private final UserService users;

  @Override
  public ResponseEntity<UserResponse> createUser(@Valid CreateUserRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(mapper.toResponse(users.create(mapper.toInput(request))));
  }

  @Override
  public ResponseEntity<UserResponse> createProvisionalUser(
      @Valid CreateProvisionalUserRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(mapper.toResponse(users.createProvisional(mapper.toInput(request))));
  }

  @Override
  public ResponseEntity<UserResponse> completeProvisionalUser(
      UUID userId, @Valid CompleteProvisionalUserRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(mapper.toResponse(users.completeProvisional(userId, mapper.toInput(request))));
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
    return ResponseEntity.ok(
        mapper.toResponse(
            users.updateCurrent(currentJwt.current().getName(), patchAdapter.toInput(request))));
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
  public ResponseEntity<List<UserResponse>> searchUsers(@Valid SearchUsersRequest request) {
    return ResponseEntity.ok(
        mapper.toResponses(
            users.searchByAuthorizationSubjects(
                request.getAuthorizationSubjects() == null
                    ? List.of()
                    : request.getAuthorizationSubjects().stream().toList())));
  }

  @Override
  public ResponseEntity<UserResponse> updateUser(UUID userId, @Valid UpdateUserRequest request) {
    return ResponseEntity.ok(
        mapper.toResponse(users.update(userId, patchAdapter.toInput(request))));
  }
}
