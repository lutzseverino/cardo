package com.odonta.billing.service;

import com.odonta.billing.model.BillingSessionResult;
import com.odonta.billing.model.PortalSessionCommand;
import com.odonta.billing.model.PortalSessionRequest;
import com.odonta.billing.model.PortalSessionResponse;
import com.odonta.billing.provider.BillingProvider;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PortalSessionService {

  private final BillingProvider provider;

  PortalSessionService(BillingProvider provider) {
    this.provider = provider;
  }

  public PortalSessionResponse create(UUID subjectId, PortalSessionRequest request) {
    BillingSessionResult session =
        provider.createPortalSession(subjectId, PortalSessionCommand.from(request));
    return new PortalSessionResponse(session.id(), session.url());
  }
}
