package io.github.lutzseverino.cardo.billing.config;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cardo.billing.stripe")
public record StripeProperties(
    String secretKey,
    String webhookSecret,
    List<CheckoutPrice> checkoutPrices,
    Duration connectTimeout,
    Duration readTimeout) {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

  public StripeProperties {
    checkoutPrices = checkoutPrices == null ? List.of() : List.copyOf(checkoutPrices);
    connectTimeout = positiveOrDefault(connectTimeout, "connect-timeout");
    readTimeout = positiveOrDefault(readTimeout, "read-timeout");
    HashSet<String> priceIds = new HashSet<>();
    HashSet<String> products = new HashSet<>();
    for (CheckoutPrice price : checkoutPrices) {
      if (price == null || blank(price.id()) || blank(price.product())) {
        throw new IllegalArgumentException(
            "cardo.billing.stripe.checkout-prices entries require non-blank id and product.");
      }
      if (!priceIds.add(price.id()) || !products.add(price.product())) {
        throw new IllegalArgumentException(
            "cardo.billing.stripe.checkout-prices must have unique ids and products.");
      }
    }
  }

  private static Duration positiveOrDefault(Duration value, String property) {
    Duration resolved = value == null ? DEFAULT_TIMEOUT : value;
    if (resolved.isZero() || resolved.isNegative() || resolved.toMillis() > Integer.MAX_VALUE) {
      throw new IllegalArgumentException(
          "cardo.billing.stripe." + property + " must be a positive millisecond duration.");
    }
    return resolved;
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  public record CheckoutPrice(String id, String product) {}
}
