package io.github.lutzseverino.cardo.billing.integration.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.lutzseverino.cardo.billing.config.StripeProperties;
import io.github.lutzseverino.cardo.common.api.ApiException;
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
                List.of(
                    new StripeProperties.CheckoutPrice("price_clinic", "clinic"),
                    new StripeProperties.CheckoutPrice("price_polity", "polity"))));

    StripeProperties.CheckoutPrice price = catalog.findByProduct("clinic");
    StripeProperties.CheckoutPrice polityPrice = catalog.findByProduct("polity");

    assertThat(price.id()).isEqualTo("price_clinic");
    assertThat(polityPrice.id()).isEqualTo("price_polity");
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
