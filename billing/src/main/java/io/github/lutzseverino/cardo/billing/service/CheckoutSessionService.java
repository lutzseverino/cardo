package io.github.lutzseverino.cardo.billing.service;

import io.github.lutzseverino.cardo.billing.model.BillingSessionResult;
import io.github.lutzseverino.cardo.billing.model.CreateCheckoutSessionInput;
import io.github.lutzseverino.cardo.billing.provider.BillingProvider;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Validated
@Service
@RequiredArgsConstructor
public class CheckoutSessionService {

  private final BillingProvider provider;

  public BillingSessionResult create(UUID subjectId, @Valid CreateCheckoutSessionInput input) {
    return provider.createCheckoutSession(
        subjectId, input.product(), input.successUrl(), input.cancelUrl());
  }
}
