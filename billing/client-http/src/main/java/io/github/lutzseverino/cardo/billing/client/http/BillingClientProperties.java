package io.github.lutzseverino.cardo.billing.client.http;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cardo.billing.client")
record BillingClientProperties(String baseUrl) {}
