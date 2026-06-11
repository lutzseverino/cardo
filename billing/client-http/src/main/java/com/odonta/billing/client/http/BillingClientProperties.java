package com.odonta.billing.client.http;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "odonta.billing.client")
record BillingClientProperties(String baseUrl) {}
