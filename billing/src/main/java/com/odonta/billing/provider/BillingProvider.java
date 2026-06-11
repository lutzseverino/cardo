package com.odonta.billing.provider;

import com.odonta.billing.model.BillingSessionResult;
import com.odonta.billing.model.CreateCheckoutSessionCommand;
import com.odonta.billing.model.CreatePortalSessionCommand;
import java.util.UUID;

public interface BillingProvider {

  BillingSessionResult createCheckoutSession(UUID subjectId, CreateCheckoutSessionCommand command);

  BillingSessionResult createPortalSession(UUID subjectId, CreatePortalSessionCommand command);
}
