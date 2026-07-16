package io.github.lutzseverino.cardo.billing.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.lutzseverino.cardo.billing.mapper.EntitlementApplicationMapper;
import io.github.lutzseverino.cardo.billing.model.CreateCheckoutSessionInput;
import io.github.lutzseverino.cardo.billing.provider.BillingProvider;
import io.github.lutzseverino.cardo.billing.repository.EntitlementRepository;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

@SpringJUnitConfig(BillingServiceValidationTest.Config.class)
class BillingServiceValidationTest {

  @Autowired private BillingProvider provider;

  @Autowired private EntitlementRepository entitlements;

  @Autowired private CheckoutSessionService checkoutSessions;

  @Autowired private EntitlementService entitlementService;

  @Test
  void validatesRequestsAtTheServiceBoundary() {
    CreateCheckoutSessionInput input =
        new CreateCheckoutSessionInput(
            "   ",
            URI.create("https://app.example.com/success"),
            URI.create("https://app.example.com/cancel"));

    assertThatThrownBy(() -> checkoutSessions.create(UUID.randomUUID(), input))
        .isInstanceOf(ConstraintViolationException.class);

    verifyNoInteractions(provider);
  }

  @Test
  void validatesScalarParametersAtTheServiceBoundary() {
    assertThatThrownBy(() -> entitlementService.getCurrent(UUID.randomUUID(), "   "))
        .isInstanceOf(ConstraintViolationException.class);

    verifyNoInteractions(entitlements);
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
    EntitlementRepository entitlements() {
      return mock(EntitlementRepository.class);
    }

    @Bean
    EntitlementApplicationMapper entitlementMapper() {
      return mock(EntitlementApplicationMapper.class);
    }

    @Bean
    CheckoutSessionService checkoutSessions(BillingProvider provider) {
      return new CheckoutSessionService(provider);
    }

    @Bean
    EntitlementService entitlementService(
        EntitlementRepository entitlements, EntitlementApplicationMapper mapper) {
      return new EntitlementService(entitlements, mapper);
    }
  }
}
