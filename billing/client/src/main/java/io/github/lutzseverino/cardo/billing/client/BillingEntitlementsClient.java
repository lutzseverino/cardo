package io.github.lutzseverino.cardo.billing.client;

import java.util.UUID;

public interface BillingEntitlementsClient {

  BillingEntitlement require(UUID subjectId, String product);
}
