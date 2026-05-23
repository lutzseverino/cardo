package com.odonta.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.odonta.billing.config.StripeProperties;
import com.odonta.common.api.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;

class StripePriceCatalogTest {

  @Test
  void findsConfiguredProductPrice() {
    StripePriceCatalog catalog =
        new StripePriceCatalog(
            new StripeProperties(
                "sk_test_123",
                "whsec_123",
                List.of(new StripeProperties.Price("price_clinic", "clinic", 1, 5))));

    StripeProperties.Price price = catalog.findByProduct("clinic");

    assertThat(price.id()).isEqualTo("price_clinic");
    assertThat(price.tenantLimit()).isEqualTo(1);
    assertThat(price.seatLimit()).isEqualTo(5);
  }

  @Test
  void rejectsUnconfiguredProductPrice() {
    StripePriceCatalog catalog =
        new StripePriceCatalog(new StripeProperties("sk_test_123", "whsec_123", List.of()));

    assertThatThrownBy(() -> catalog.findByProduct("clinic"))
        .isInstanceOf(ApiException.class)
        .hasMessage("No billing price is configured for this product.");
  }
}
