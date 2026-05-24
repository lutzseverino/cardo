package com.odonta.billing.model;

public record PortalSessionCommand(String returnUrl) {

  public static PortalSessionCommand from(PortalSessionRequest request) {
    return new PortalSessionCommand(request.returnUrl());
  }
}
