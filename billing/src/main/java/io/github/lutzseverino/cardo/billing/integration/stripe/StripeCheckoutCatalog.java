package io.github.lutzseverino.cardo.billing.integration.stripe;

import io.github.lutzseverino.cardo.billing.config.StripeProperties;
import io.github.lutzseverino.cardo.common.api.ApiException;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class StripeCheckoutCatalog {

  private final List<StripeProperties.CheckoutPrice> prices;

  StripeCheckoutCatalog(StripeProperties properties) {
    this.prices = properties.checkoutPrices() == null ? List.of() : properties.checkoutPrices();
  }

  StripeProperties.CheckoutPrice findByProduct(String product) {
    return prices.stream()
        .filter(price -> product.equals(price.product()))
        .filter(price -> StringUtils.hasText(price.id()))
        .findFirst()
        .orElseThrow(
            () ->
                ApiException.badRequest(
                    "billing_checkout_price_not_configured",
                    "No checkout price is configured for this product."));
  }
}
