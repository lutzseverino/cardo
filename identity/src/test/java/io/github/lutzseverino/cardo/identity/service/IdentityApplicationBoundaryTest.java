package io.github.lutzseverino.cardo.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lutzseverino.cardo.identity.config.IdentityRuntimeContractInitializer;
import io.github.lutzseverino.cardo.identity.workflow.InitializeIdentityRuntimeWorkflow;
import io.github.lutzseverino.cardo.identity.workflow.ReconcileIdentityOperationsWorkflow;
import jakarta.persistence.Entity;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

class IdentityApplicationBoundaryTest {

  @Test
  void serviceContractsExcludeTransportPersistenceEntitiesAndProviderSdks() {
    assertApplicationBoundary(
        AuthenticationService.class,
        IdentityOperationService.class,
        UserService.class,
        InitializeIdentityRuntimeWorkflow.class,
        ReconcileIdentityOperationsWorkflow.class);
  }

  @Test
  void everyServiceBeanIsCoveredByTheBoundaryContractTest() {
    assertThat(scan(Service.class, "io.github.lutzseverino.cardo.identity.service"))
        .containsExactlyInAnyOrder(
            AuthenticationService.class, IdentityOperationService.class, UserService.class);
  }

  @Test
  void servicesDoNotDependOnWorkflows() {
    assertNoWorkflowDependency(AuthenticationService.class);
    assertNoWorkflowDependency(IdentityOperationService.class);
    assertNoWorkflowDependency(UserService.class);
  }

  @Test
  void runtimeInitializationIsAWorkflowEntryPoint() {
    assertThat(InitializeIdentityRuntimeWorkflow.class.isAnnotationPresent(Component.class))
        .isTrue();
    assertThat(
            Arrays.stream(InitializeIdentityRuntimeWorkflow.class.getDeclaredFields())
                .map(field -> field.getType().getSimpleName())
                .noneMatch(name -> name.endsWith("Workflow")))
        .isTrue();
  }

  @Test
  void authenticationExtractionAndStartupTriggeringRemainInboundAdapterResponsibilities() {
    assertThat(
            Arrays.stream(AuthenticationService.class.getDeclaredFields())
                .map(field -> field.getType().getSimpleName())
                .noneMatch(name -> name.equals("CurrentJwtReader") || name.equals("JwtDecoder")))
        .isTrue();
    assertThat(
            Arrays.stream(AuthenticationService.class.getDeclaredFields())
                .map(field -> field.getType().getPackageName())
                .noneMatch(packageName -> packageName.contains(".integration")))
        .isTrue();
    assertThat(
            Arrays.stream(IdentityRuntimeContractInitializer.class.getDeclaredFields())
                .map(field -> field.getType().getSimpleName())
                .noneMatch(name -> name.endsWith("Repository") || name.endsWith("Provider")))
        .isTrue();
  }

  @Test
  void controllersDoNotDependOnRepositoriesOrResolvers() {
    scan(RestController.class, "io.github.lutzseverino.cardo.identity.controller")
        .forEach(this::assertNoPersistenceDependency);
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

  private void assertNoWorkflowDependency(Class<?> service) {
    assertThat(
            Arrays.stream(service.getDeclaredFields())
                .map(field -> field.getType().getSimpleName())
                .noneMatch(name -> name.endsWith("Workflow")))
        .as(service.getName())
        .isTrue();
  }

  private void assertNoPersistenceDependency(Class<?> controller) {
    assertThat(
            Arrays.stream(controller.getDeclaredFields())
                .map(field -> field.getType().getSimpleName())
                .noneMatch(name -> name.endsWith("Repository") || name.endsWith("Resolver")))
        .as(controller.getName())
        .isTrue();
  }

  private List<Class<?>> scan(Class<?> annotation, String packageName) {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(
        new AnnotationTypeFilter(annotation.asSubclass(java.lang.annotation.Annotation.class)));
    return scanner.findCandidateComponents(packageName).stream()
        .<Class<?>>map(definition -> load(definition.getBeanClassName()))
        .toList();
  }

  private Class<?> load(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException exception) {
      throw new AssertionError(exception);
    }
  }

  private boolean forbidden(Type type) {
    if (type instanceof Class<?> value) {
      return value.getPackageName().contains(".api.model")
          || value.getSimpleName().endsWith("Projection")
          || value.isAnnotationPresent(Entity.class)
          || value.getPackageName().startsWith("com.stripe")
          || value.getPackageName().startsWith("org.keycloak");
    }
    if (type instanceof ParameterizedType value) {
      return forbidden(value.getRawType())
          || Arrays.stream(value.getActualTypeArguments()).anyMatch(this::forbidden);
    }
    return false;
  }
}
