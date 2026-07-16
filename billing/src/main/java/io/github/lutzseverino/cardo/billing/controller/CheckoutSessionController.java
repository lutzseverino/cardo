package io.github.lutzseverino.cardo.billing.controller;

import io.github.lutzseverino.cardo.authorization.spring.AuthenticatedUserReader;
import io.github.lutzseverino.cardo.billing.api.CheckoutSessionsApi;
import io.github.lutzseverino.cardo.billing.api.model.CheckoutSessionResponse;
import io.github.lutzseverino.cardo.billing.api.model.CreateCheckoutSessionRequest;
import io.github.lutzseverino.cardo.billing.mapper.BillingSessionTransportMapper;
import io.github.lutzseverino.cardo.billing.service.CheckoutSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${cardo.api.base-path}")
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
