package io.github.lutzseverino.cardo.billing.provider;

import io.github.lutzseverino.cardo.billing.model.BillingSessionResult;
import java.net.URI;
import java.util.UUID;

public interface BillingProvider {

  BillingSessionResult createCheckoutSession(
      UUID subjectId, String product, URI successUrl, URI cancelUrl);

  BillingSessionResult createPortalSession(UUID subjectId, URI returnUrl);
}
