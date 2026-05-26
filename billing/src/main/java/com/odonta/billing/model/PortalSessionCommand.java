package com.odonta.billing.model;

import com.odonta.billing.api.model.PortalSessionRequest;

public record PortalSessionCommand(String returnUrl) {

  public static PortalSessionCommand from(PortalSessionRequest request) {
    return new PortalSessionCommand(request.getReturnUrl().toString());
  }
}
