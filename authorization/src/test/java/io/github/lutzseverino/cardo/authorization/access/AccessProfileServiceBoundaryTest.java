package io.github.lutzseverino.cardo.authorization.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;

class AccessProfileServiceBoundaryTest {

  @Test
  void declaresTheEmbeddedApplicationServiceRole() {
    assertThat(AccessProfileService.class.isAnnotationPresent(Service.class)).isTrue();
  }

  @Test
  void exposesApplicationResultsForProfileQueries() {
    AccessProfileRepository profiles = mock(AccessProfileRepository.class);
    UUID profileId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    AccessProfileProjection projection = projection(profileId, tenantId);
    when(profiles.findAvailable("clinic", tenantId)).thenReturn(List.of(projection));
    when(profiles.findAvailableById(profileId, "clinic", tenantId))
        .thenReturn(Optional.of(projection));
    AccessProfileService service =
        new AccessProfileService(profiles, mock(AccessProfileGrantRepository.class));

    assertThat(service.listAvailable("clinic", tenantId))
        .containsExactly(
            new AccessProfileResult(profileId, "clinic", tenantId, "Staff", null, false));
    assertThat(service.getAvailable(profileId, "clinic", tenantId))
        .contains(new AccessProfileResult(profileId, "clinic", tenantId, "Staff", null, false));
  }

  @Test
  void publicContractsDoNotExposePersistenceProjections() {
    Arrays.stream(AccessProfileService.class.getDeclaredMethods())
        .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
        .forEach(
            method -> {
              assertThat(containsProjection(method.getGenericReturnType()))
                  .as(method.toString())
                  .isFalse();
              Arrays.stream(method.getGenericParameterTypes())
                  .forEach(
                      type -> assertThat(containsProjection(type)).as(method.toString()).isFalse());
            });
  }

  private boolean containsProjection(Type type) {
    if (type instanceof Class<?> value) {
      return value.getSimpleName().endsWith("Projection");
    }
    if (type instanceof ParameterizedType value) {
      return containsProjection(value.getRawType())
          || Arrays.stream(value.getActualTypeArguments()).anyMatch(this::containsProjection);
    }
    return false;
  }

  private AccessProfileProjection projection(UUID profileId, UUID tenantId) {
    return new AccessProfileProjection() {
      public UUID getId() {
        return profileId;
      }

      public String getProduct() {
        return "clinic";
      }

      public UUID getTenantId() {
        return tenantId;
      }

      public String getName() {
        return "Staff";
      }

      public String getDescription() {
        return null;
      }

      public boolean isTemplate() {
        return false;
      }
    };
  }
}
