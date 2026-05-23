package com.odonta.billing.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "odonta.billing.client")
public record BillingClientProperties(String baseUrl) {}
