package com.odonta.billing.model;

public record CheckoutSessionCommand(String product, String successUrl, String cancelUrl) {}
