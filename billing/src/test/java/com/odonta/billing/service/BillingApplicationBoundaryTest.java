package com.odonta.billing.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.odonta.billing.BillingPermissions;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

class BillingApplicationBoundaryTest {

  @Test
  void subjectEntitlementAuthorizationLivesOnServiceMethods() throws Exception {
    assertThat(
            method(EntitlementService.class, "get", UUID.class, String.class)
                .isAnnotationPresent(PreAuthorize.class))
        .isTrue();
    assertThat(
            method(EntitlementService.class, "require", UUID.class, String.class)
                .isAnnotationPresent(PreAuthorize.class))
        .isTrue();
    assertThat(
            method(EntitlementService.class, "getCurrent", UUID.class, String.class)
                .isAnnotationPresent(PreAuthorize.class))
        .isFalse();
    assertThat(
            method(EntitlementService.class, "get", UUID.class, String.class)
                .getAnnotation(PreAuthorize.class)
                .value())
        .contains(BillingPermissions.ENTITLEMENT_READ_AUTHORITY);
  }

  @Test
  void persistenceChangingOperationsAreTransactional() throws Exception {
    assertThat(
            method(
                    CustomerService.class,
                    "getOrCreate",
                    UUID.class,
                    String.class,
                    java.util.function.Supplier.class)
                .isAnnotationPresent(Transactional.class))
        .isTrue();
    assertThat(
            method(EntitlementService.class, "replaceActive", UUID.class, Collection.class)
                .isAnnotationPresent(Transactional.class))
        .isTrue();
  }

  private Method method(Class<?> type, String name, Class<?>... parameterTypes) throws Exception {
    return type.getMethod(name, parameterTypes);
  }
}
