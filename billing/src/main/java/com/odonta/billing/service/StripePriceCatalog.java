package com.odonta.billing.service;

import com.odonta.billing.config.StripeProperties;
import com.odonta.common.api.ApiException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class StripePriceCatalog {

  private final List<StripeProperties.Price> prices;

  StripePriceCatalog(StripeProperties properties) {
    this.prices = properties.prices() == null ? List.of() : properties.prices();
  }

  StripeProperties.Price findByProduct(String product) {
    return prices.stream()
        .filter(price -> product.equals(price.product()))
        .filter(price -> StringUtils.hasText(price.id()))
        .findFirst()
        .orElseThrow(
            () ->
                ApiException.badRequest(
                    "billing_price_not_configured",
                    "No billing price is configured for this product."));
  }
}
