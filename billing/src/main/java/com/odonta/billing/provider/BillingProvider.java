package com.odonta.billing.provider;

import com.odonta.billing.model.BillingSessionResult;
import java.net.URI;
import java.util.UUID;

public interface BillingProvider {

  BillingSessionResult createCheckoutSession(
      UUID subjectId, String product, URI successUrl, URI cancelUrl);

  BillingSessionResult createPortalSession(UUID subjectId, URI returnUrl);
}
