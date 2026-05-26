package com.odonta.billing.service;

import com.odonta.billing.api.model.CheckoutSessionRequest;
import com.odonta.billing.api.model.CheckoutSessionResponse;
import com.odonta.billing.model.BillingSessionResult;
import com.odonta.billing.model.CheckoutSessionCommand;
import com.odonta.billing.provider.BillingProvider;
import java.net.URI;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CheckoutSessionService {

  private final BillingProvider provider;

  CheckoutSessionService(BillingProvider provider) {
    this.provider = provider;
  }

  public CheckoutSessionResponse create(UUID subjectId, CheckoutSessionRequest request) {
    BillingSessionResult session =
        provider.createCheckoutSession(subjectId, CheckoutSessionCommand.from(request));
    return new CheckoutSessionResponse(session.id(), URI.create(session.url()));
  }
}
