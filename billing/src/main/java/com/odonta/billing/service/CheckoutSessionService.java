package com.odonta.billing.service;

import com.odonta.billing.api.model.CreateCheckoutSessionInput;
import com.odonta.billing.model.BillingSessionResult;
import com.odonta.billing.provider.BillingProvider;
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
        subjectId, input.getProduct(), input.getSuccessUrl(), input.getCancelUrl());
  }
}
