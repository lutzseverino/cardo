package com.odonta.billing.controller;

import com.odonta.authorization.spring.AuthenticatedUserReader;
import com.odonta.billing.model.CheckoutSessionRequest;
import com.odonta.billing.model.CheckoutSessionResponse;
import com.odonta.billing.service.CheckoutSessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${odonta.api.base-path}/billing/checkout/sessions")
public class CheckoutSessionController {

  private final CheckoutSessionService checkoutSessions;
  private final AuthenticatedUserReader users;

  CheckoutSessionController(
      CheckoutSessionService checkoutSessions, AuthenticatedUserReader users) {
    this.checkoutSessions = checkoutSessions;
    this.users = users;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  CheckoutSessionResponse create(
      JwtAuthenticationToken authentication, @RequestBody @Valid CheckoutSessionRequest request) {
    return checkoutSessions.create(users.currentUser(authentication).id(), request);
  }
}
