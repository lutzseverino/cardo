package com.odonta.billing.client;

import java.util.UUID;

public interface BillingEntitlementsClient {

  Integer requireTenantLimit(UUID subjectId, String product);
}
