package com.odonta.billing.integration.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.odonta.billing.config.StripeProperties;
import com.odonta.common.api.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;

class StripeCheckoutCatalogTest {

  @Test
  void findsConfiguredProductPrice() {
    StripeCheckoutCatalog catalog =
        new StripeCheckoutCatalog(
            new StripeProperties(
                "sk_test_123",
                "whsec_123",
                List.of(new StripeProperties.CheckoutPrice("price_clinic", "clinic"))));

    StripeProperties.CheckoutPrice price = catalog.findByProduct("clinic");

    assertThat(price.id()).isEqualTo("price_clinic");
  }

  @Test
  void rejectsUnconfiguredProductPrice() {
    StripeCheckoutCatalog catalog =
        new StripeCheckoutCatalog(new StripeProperties("sk_test_123", "whsec_123", List.of()));

    assertThatThrownBy(() -> catalog.findByProduct("clinic"))
        .isInstanceOf(ApiException.class)
        .hasMessage("No checkout price is configured for this product.");
  }
}
