package io.github.lutzseverino.cardo.billing.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cardo.billing.stripe")
public record StripeProperties(
    String secretKey, String webhookSecret, List<CheckoutPrice> checkoutPrices) {

  public record CheckoutPrice(String id, String product) {}
}
