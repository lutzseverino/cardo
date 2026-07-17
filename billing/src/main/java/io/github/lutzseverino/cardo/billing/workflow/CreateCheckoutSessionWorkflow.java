package io.github.lutzseverino.cardo.billing.workflow;

import io.github.lutzseverino.cardo.billing.model.BillingSessionResult;
import io.github.lutzseverino.cardo.billing.model.CreateCheckoutSessionInput;
import io.github.lutzseverino.cardo.billing.model.CustomerResult;
import io.github.lutzseverino.cardo.billing.provider.BillingProvider;
import io.github.lutzseverino.cardo.billing.service.CustomerService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Validated
@Component
@RequiredArgsConstructor
public class CreateCheckoutSessionWorkflow {

  private final BillingProvider provider;
  private final CustomerService customers;

  public BillingSessionResult create(UUID subjectId, @Valid CreateCheckoutSessionInput input) {
    CustomerResult customer = customers.getOrCreate(subjectId);
    return provider.createCheckoutSession(
        subjectId,
        customer.providerCustomerId(),
        input.product(),
        input.successUrl(),
        input.cancelUrl());
  }
}
