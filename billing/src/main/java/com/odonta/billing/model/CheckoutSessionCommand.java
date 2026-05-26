package com.odonta.billing.model;

import com.odonta.billing.api.model.CheckoutSessionRequest;

public record CheckoutSessionCommand(String product, String successUrl, String cancelUrl) {

  public static CheckoutSessionCommand from(CheckoutSessionRequest request) {
    return new CheckoutSessionCommand(
        request.getProduct(),
        request.getSuccessUrl().toString(),
        request.getCancelUrl().toString());
  }
}
