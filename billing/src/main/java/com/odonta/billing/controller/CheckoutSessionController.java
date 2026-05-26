package com.odonta.billing.controller;

import com.odonta.authorization.spring.AuthenticatedUserReader;
import com.odonta.billing.api.CheckoutSessionsApi;
import com.odonta.billing.api.model.CheckoutSessionRequest;
import com.odonta.billing.api.model.CheckoutSessionResponse;
import com.odonta.billing.service.CheckoutSessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${odonta.api.base-path}")
public class CheckoutSessionController implements CheckoutSessionsApi {

  private final CheckoutSessionService checkoutSessions;
  private final AuthenticatedUserReader users;

  CheckoutSessionController(
      CheckoutSessionService checkoutSessions, AuthenticatedUserReader users) {
    this.checkoutSessions = checkoutSessions;
    this.users = users;
  }

  @Override
  public ResponseEntity<CheckoutSessionResponse> createCheckoutSession(
      @Valid CheckoutSessionRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(checkoutSessions.create(users.currentUser().id(), request));
  }
}
