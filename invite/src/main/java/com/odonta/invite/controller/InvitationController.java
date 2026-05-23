package com.odonta.invite.controller;

import com.odonta.authorization.spring.AuthenticatedUserReader;
import com.odonta.invite.model.CompleteInvitationRequest;
import com.odonta.invite.model.CreateInvitationRequest;
import com.odonta.invite.model.CreateInvitationResponse;
import com.odonta.invite.model.InvitationCompletionResponse;
import com.odonta.invite.model.InvitationResponse;
import com.odonta.invite.service.InvitationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${odonta.api.base-path}/invitations")
public class InvitationController {

  private final AuthenticatedUserReader users;
  private final InvitationService invitations;

  InvitationController(AuthenticatedUserReader users, InvitationService invitations) {
    this.users = users;
    this.invitations = invitations;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  CreateInvitationResponse create(
      JwtAuthenticationToken authentication, @RequestBody @Valid CreateInvitationRequest request) {
    return invitations.create(users.currentUser(authentication), request);
  }

  @GetMapping("/{token}")
  InvitationResponse get(@PathVariable String token) {
    return invitations.get(token);
  }

  @PostMapping("/{token}/completion")
  @ResponseStatus(HttpStatus.CREATED)
  InvitationCompletionResponse complete(
      @PathVariable String token, @RequestBody @Valid CompleteInvitationRequest request) {
    return invitations.complete(token, request);
  }

  @PostMapping("/{token}/acceptance")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void accept(JwtAuthenticationToken authentication, @PathVariable String token) {
    invitations.accept(token, users.currentUser(authentication));
  }
}
