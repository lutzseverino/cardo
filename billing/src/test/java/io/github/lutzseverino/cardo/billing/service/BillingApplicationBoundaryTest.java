package io.github.lutzseverino.cardo.billing.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lutzseverino.cardo.billing.BillingPermissions;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

class BillingApplicationBoundaryTest {

  @Test
  void serviceContractsExcludeTransportAndPersistenceTypes() {
    assertApplicationBoundary(
        CheckoutSessionService.class,
        CustomerService.class,
        EntitlementService.class,
        PortalSessionService.class);
  }

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

  private void assertApplicationBoundary(Class<?>... services) {
    Arrays.stream(services)
        .flatMap(service -> Arrays.stream(service.getDeclaredMethods()))
        .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
        .forEach(
            method -> {
              assertThat(forbidden(method.getGenericReturnType())).as(method.toString()).isFalse();
              Arrays.stream(method.getGenericParameterTypes())
                  .forEach(type -> assertThat(forbidden(type)).as(method.toString()).isFalse());
            });
  }

  private boolean forbidden(Type type) {
    if (type instanceof Class<?> value) {
      return value.getPackageName().contains(".api.model")
          || value.getSimpleName().endsWith("Projection");
    }
    if (type instanceof ParameterizedType value) {
      return forbidden(value.getRawType())
          || Arrays.stream(value.getActualTypeArguments()).anyMatch(this::forbidden);
    }
    return false;
  }
}
