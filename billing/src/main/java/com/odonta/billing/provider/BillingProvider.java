package com.odonta.billing.provider;

import com.odonta.billing.model.BillingSessionResult;
import com.odonta.billing.model.CheckoutSessionCommand;
import com.odonta.billing.model.PortalSessionCommand;
import java.util.UUID;

public interface BillingProvider {

  BillingSessionResult createCheckoutSession(UUID subjectId, CheckoutSessionCommand command);

  BillingSessionResult createPortalSession(UUID subjectId, PortalSessionCommand command);
}
