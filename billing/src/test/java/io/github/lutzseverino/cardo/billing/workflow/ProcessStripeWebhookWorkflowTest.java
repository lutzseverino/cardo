package io.github.lutzseverino.cardo.billing.workflow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.billing.model.CustomerResult;
import io.github.lutzseverino.cardo.billing.model.EntitlementSyncItem;
import io.github.lutzseverino.cardo.billing.model.ProviderEvent;
import io.github.lutzseverino.cardo.billing.provider.StripeWebhookEvent;
import io.github.lutzseverino.cardo.billing.provider.StripeWebhookProvider;
import io.github.lutzseverino.cardo.billing.repository.ProviderEventRepository;
import io.github.lutzseverino.cardo.billing.service.CustomerService;
import io.github.lutzseverino.cardo.billing.service.EntitlementService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProcessStripeWebhookWorkflowTest {

  @Test
  void coordinatesCustomerAndEntitlementOwnersForActiveEntitlementEvents() {
    CustomerService customers = mock(CustomerService.class);
    EntitlementService entitlements = mock(EntitlementService.class);
    ProviderEventRepository events = mock(ProviderEventRepository.class);
    StripeWebhookProvider stripe = mock(StripeWebhookProvider.class);
    ProcessStripeWebhookWorkflow workflow =
        new ProcessStripeWebhookWorkflow(customers, entitlements, events, stripe);
    UUID subjectId = UUID.randomUUID();
    StripeWebhookEvent event =
        new StripeWebhookEvent("evt_1", "entitlements.active_entitlement_summary.updated", "cus_1");
    List<EntitlementSyncItem> active = List.of(new EntitlementSyncItem("clinic", 1, 5));
    when(stripe.parse("payload", "signature")).thenReturn(event);
    when(stripe.name()).thenReturn("stripe");
    when(stripe.updatesActiveEntitlements(event)).thenReturn(true);
    when(customers.getByProviderCustomerId("stripe", "cus_1"))
        .thenReturn(new CustomerResult(UUID.randomUUID(), subjectId, "stripe", "cus_1"));
    when(stripe.activeEntitlements("cus_1")).thenReturn(active);

    workflow.process("payload", "signature");

    verify(entitlements).replaceActive(subjectId, active);
    verify(events).save(any(ProviderEvent.class));
  }
}
