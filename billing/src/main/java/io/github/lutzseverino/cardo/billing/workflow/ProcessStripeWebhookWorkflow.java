package io.github.lutzseverino.cardo.billing.workflow;

import io.github.lutzseverino.cardo.billing.model.CustomerResult;
import io.github.lutzseverino.cardo.billing.model.ProviderEvent;
import io.github.lutzseverino.cardo.billing.provider.StripeWebhookEvent;
import io.github.lutzseverino.cardo.billing.provider.StripeWebhookProvider;
import io.github.lutzseverino.cardo.billing.repository.ProviderEventRepository;
import io.github.lutzseverino.cardo.billing.service.CustomerService;
import io.github.lutzseverino.cardo.billing.service.EntitlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ProcessStripeWebhookWorkflow {

  private final CustomerService customers;
  private final EntitlementService entitlements;
  private final ProviderEventRepository events;
  private final StripeWebhookProvider stripe;

  @Transactional
  public void process(String payload, String signature) {
    StripeWebhookEvent event = stripe.parse(payload, signature);
    if (events.existsByProviderAndProviderEventId(stripe.name(), event.id())) {
      return;
    }
    if (stripe.updatesActiveEntitlements(event)) {
      syncActiveEntitlements(event.providerCustomerId());
    }
    events.save(ProviderEvent.processed(stripe.name(), event.id(), event.type()));
  }

  private void syncActiveEntitlements(String providerCustomerId) {
    CustomerResult customer = customers.getByProviderCustomerId(stripe.name(), providerCustomerId);
    entitlements.replaceActive(customer.subjectId(), stripe.activeEntitlements(providerCustomerId));
  }
}
