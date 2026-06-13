package com.odonta.billing.service;

import com.odonta.billing.api.model.CreatePortalSessionInput;
import com.odonta.billing.model.BillingSessionResult;
import com.odonta.billing.provider.BillingProvider;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Validated
@Service
@RequiredArgsConstructor
public class PortalSessionService {

  private final BillingProvider provider;

  public BillingSessionResult create(UUID subjectId, @Valid CreatePortalSessionInput input) {
    return provider.createPortalSession(subjectId, input.getReturnUrl());
  }
}
