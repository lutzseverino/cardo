package com.odonta.billing.controller;

import com.odonta.authorization.spring.AuthenticatedUserReader;
import com.odonta.billing.api.CheckoutSessionsApi;
import com.odonta.billing.api.model.CheckoutSessionResponse;
import com.odonta.billing.api.model.CreateCheckoutSessionRequest;
import com.odonta.billing.mapper.BillingSessionTransportMapper;
import com.odonta.billing.service.CheckoutSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${odonta.api.base-path}")
@RequiredArgsConstructor
public class CheckoutSessionController implements CheckoutSessionsApi {

  private final BillingSessionTransportMapper mapper;
  private final CheckoutSessionService checkoutSessions;
  private final AuthenticatedUserReader users;

  @Override
  public ResponseEntity<CheckoutSessionResponse> createCheckoutSession(
      @Valid CreateCheckoutSessionRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            mapper.toCheckoutResponse(
                checkoutSessions.create(users.currentUser().id(), mapper.toInput(request))));
  }
}
