package io.github.lutzseverino.cardo.billing.provider;

public record StripeWebhookEvent(String id, String type, String providerCustomerId) {}
