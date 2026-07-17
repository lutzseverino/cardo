package io.github.lutzseverino.cardo.billing.provider;

import io.github.lutzseverino.cardo.billing.model.BillingSessionResult;
import java.net.URI;
import java.util.UUID;

public interface BillingProvider {

  String name();

  String createCustomer(UUID subjectId);

  void deleteCustomer(String providerCustomerId);

  BillingSessionResult createCheckoutSession(
      UUID subjectId, String providerCustomerId, String product, URI successUrl, URI cancelUrl);

  BillingSessionResult createPortalSession(String providerCustomerId, URI returnUrl);
}
