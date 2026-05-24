package com.odonta.billing.model;

public record CheckoutSessionCommand(String product, String successUrl, String cancelUrl) {

  public static CheckoutSessionCommand from(CheckoutSessionRequest request) {
    return new CheckoutSessionCommand(request.product(), request.successUrl(), request.cancelUrl());
  }
}
