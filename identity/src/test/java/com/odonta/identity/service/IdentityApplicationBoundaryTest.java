package com.odonta.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class IdentityApplicationBoundaryTest {

  @Test
  void serviceContractsExcludeTransportAndPersistenceTypes() {
    assertApplicationBoundary(AuthenticationService.class, UserService.class);
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
