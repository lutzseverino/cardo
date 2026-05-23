package com.odonta.billing.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "odonta.billing.stripe")
public record StripeProperties(String secretKey, String webhookSecret, List<Price> prices) {

  public record Price(String id, String product, Integer tenantLimit, Integer seatLimit) {}
}
