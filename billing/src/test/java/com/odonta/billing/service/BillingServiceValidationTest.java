package com.odonta.billing.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.odonta.billing.model.CreateCheckoutSessionCommand;
import com.odonta.billing.provider.BillingProvider;
import jakarta.validation.ConstraintViolationException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

@SpringJUnitConfig(BillingServiceValidationTest.Config.class)
class BillingServiceValidationTest {

  @Autowired private BillingProvider provider;

  @Autowired private CheckoutSessionService checkoutSessions;

  @Test
  void validatesCommandsAtTheServiceBoundary() {
    CreateCheckoutSessionCommand command =
        new CreateCheckoutSessionCommand(
            "   ", "https://app.example.com/success", "https://app.example.com/cancel");

    assertThatThrownBy(() -> checkoutSessions.create(UUID.randomUUID(), command))
        .isInstanceOf(ConstraintViolationException.class);

    verifyNoInteractions(provider);
  }

  static class Config {

    @Bean
    static MethodValidationPostProcessor methodValidationPostProcessor() {
      return new MethodValidationPostProcessor();
    }

    @Bean
    BillingProvider provider() {
      return mock(BillingProvider.class);
    }

    @Bean
    CheckoutSessionService checkoutSessions(BillingProvider provider) {
      return new CheckoutSessionService(provider);
    }
  }
}
