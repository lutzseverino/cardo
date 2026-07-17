package io.github.lutzseverino.cardo.identity.controller;

import io.github.lutzseverino.cardo.identity.api.UsersApi;
import io.github.lutzseverino.cardo.identity.api.model.CreateProvisionalUserRequest;
import io.github.lutzseverino.cardo.identity.api.model.CreateUserRequest;
import io.github.lutzseverino.cardo.identity.api.model.IdentityOperationResponse;
import io.github.lutzseverino.cardo.identity.api.model.SearchUsersRequest;
import io.github.lutzseverino.cardo.identity.api.model.UpdateCurrentUserRequest;
import io.github.lutzseverino.cardo.identity.api.model.UpdateUserRequest;
import io.github.lutzseverino.cardo.identity.api.model.UserResponse;
import io.github.lutzseverino.cardo.identity.mapper.IdentityOperationTransportMapper;
import io.github.lutzseverino.cardo.identity.mapper.UserTransportMapper;
import io.github.lutzseverino.cardo.identity.patch.UserPatchAdapter;
import io.github.lutzseverino.cardo.identity.reader.CurrentJwtReader;
import io.github.lutzseverino.cardo.identity.service.IdentityOperationService;
import io.github.lutzseverino.cardo.identity.service.UserService;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${cardo.api.base-path}")
@RequiredArgsConstructor
public class UserController implements UsersApi {

  private final UserTransportMapper mapper;
  private final IdentityOperationTransportMapper operationMapper;
  private final UserPatchAdapter patchAdapter;
  private final CurrentJwtReader currentJwt;
  private final UserService users;
  private final IdentityOperationService operations;

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
  public ResponseEntity<IdentityOperationResponse> requestCredentialSetup(
      UUID userId, UUID operationId, OffsetDateTime notAfter) {
    return ResponseEntity.accepted()
        .body(
            operationMapper.toResponse(
                operations.requestCredentialSetup(operationId, userId, notAfter)));
  }

  @Override
  public ResponseEntity<IdentityOperationResponse> getCredentialSetup(
      UUID userId, UUID operationId) {
    return ResponseEntity.ok(
        operationMapper.toResponse(operations.getCredentialSetup(operationId, userId)));
  }

  @Override
  public ResponseEntity<IdentityOperationResponse> cancelProvisionalUser(UUID userId) {
    return ResponseEntity.accepted()
        .body(operationMapper.toResponse(operations.requestProvisionalDeletion(userId)));
  }

  @Override
  public ResponseEntity<IdentityOperationResponse> getProvisionalDeletion(UUID userId) {
    return ResponseEntity.ok(operationMapper.toResponse(operations.getProvisionalDeletion(userId)));
  }

  @Override
  public ResponseEntity<UserResponse> getCurrentUser() {
    return ResponseEntity.ok(
        mapper.toResponse(users.getCurrent(currentJwt.authorizationSubject())));
  }

  @Override
  public ResponseEntity<UserResponse> updateCurrentUser(@Valid UpdateCurrentUserRequest request) {
    return ResponseEntity.ok(
        mapper.toResponse(
            users.updateCurrent(currentJwt.authorizationSubject(), patchAdapter.toInput(request))));
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
