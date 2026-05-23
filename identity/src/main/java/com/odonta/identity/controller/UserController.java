package com.odonta.identity.controller;

import com.odonta.identity.IdentityPermissions;
import com.odonta.identity.model.CompleteProvisionalUserRequest;
import com.odonta.identity.model.CreateProvisionalUserRequest;
import com.odonta.identity.model.CreateUserRequest;
import com.odonta.identity.model.UpdateCurrentUserRequest;
import com.odonta.identity.model.UpdateUserRequest;
import com.odonta.identity.model.UserResponse;
import com.odonta.identity.service.UserService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${odonta.api.base-path}/identity/users")
public class UserController {

  private final UserService users;

  UserController(UserService users) {
    this.users = users;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  UserResponse create(@RequestBody @Valid CreateUserRequest request) {
    return users.create(request);
  }

  @PostMapping("/provisional")
  @PreAuthorize("hasAuthority('" + IdentityPermissions.USER_PROVISION_AUTHORITY + "')")
  @ResponseStatus(HttpStatus.CREATED)
  UserResponse createProvisional(@RequestBody @Valid CreateProvisionalUserRequest request) {
    return users.createProvisional(request);
  }

  @PostMapping("/{id}/completion")
  @PreAuthorize("hasAuthority('" + IdentityPermissions.USER_PROVISION_AUTHORITY + "')")
  @ResponseStatus(HttpStatus.CREATED)
  UserResponse completeProvisional(
      @PathVariable UUID id, @RequestBody @Valid CompleteProvisionalUserRequest request) {
    return users.completeProvisional(id, request);
  }

  @GetMapping("/me")
  @PreAuthorize("hasAuthority('" + IdentityPermissions.PROFILE_READ_AUTHORITY + "')")
  UserResponse getMe(Authentication authentication) {
    return users.getCurrent(authentication.getName());
  }

  @PatchMapping("/me")
  @PreAuthorize("hasAuthority('" + IdentityPermissions.PROFILE_WRITE_AUTHORITY + "')")
  UserResponse updateMe(
      Authentication authentication, @RequestBody @Valid UpdateCurrentUserRequest request) {
    return users.updateCurrent(authentication.getName(), request);
  }

  @GetMapping("/{id}")
  @PreAuthorize(
      "hasPermission(#id, '"
          + IdentityPermissions.USER_RESOURCE
          + "', '"
          + IdentityPermissions.READ
          + "')")
  UserResponse get(@PathVariable UUID id) {
    return users.get(id);
  }

  @GetMapping
  @PreAuthorize(
      "hasPermission('*', '"
          + IdentityPermissions.USER_RESOURCE
          + "', '"
          + IdentityPermissions.READ
          + "')")
  UserResponse getByEmail(@RequestParam String email) {
    return users.getByEmail(email);
  }

  @PatchMapping("/{id}")
  @PreAuthorize(
      "hasPermission(#id, '"
          + IdentityPermissions.USER_RESOURCE
          + "', '"
          + IdentityPermissions.WRITE
          + "')")
  UserResponse update(@PathVariable UUID id, @RequestBody @Valid UpdateUserRequest request) {
    return users.update(id, request);
  }
}
