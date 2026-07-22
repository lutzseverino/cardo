package io.github.lutzseverino.cardo.invite.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lutzseverino.cardo.invite.workflow.AcceptInvitationWorkflow;
import io.github.lutzseverino.cardo.invite.workflow.CreateInvitationWorkflow;
import io.github.lutzseverino.cardo.invite.workflow.ReconcileInvitationCompletionsWorkflow;
import io.github.lutzseverino.cardo.invite.workflow.RevokeInvitationWorkflow;
import jakarta.persistence.Entity;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

class InviteApplicationBoundaryTest {

  private static final Class<?>[] APPLICATION_BOUNDARIES = {
    InvitationService.class,
    InvitationCompletionService.class,
    InvitationGrantConvergenceService.class,
    AcceptInvitationWorkflow.class,
    CreateInvitationWorkflow.class,
    ReconcileInvitationCompletionsWorkflow.class,
    RevokeInvitationWorkflow.class
  };

  @Test
  void applicationContractsExcludeTransportPersistenceEntitiesAndProviderSdks() {
    Arrays.stream(APPLICATION_BOUNDARIES)
        .flatMap(boundary -> Arrays.stream(boundary.getDeclaredMethods()))
        .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
        .forEach(
            method -> {
              assertThat(forbidden(method.getGenericReturnType())).as(method.toString()).isFalse();
              Arrays.stream(method.getGenericParameterTypes())
                  .forEach(type -> assertThat(forbidden(type)).as(method.toString()).isFalse());
            });
  }

  @Test
  void everyServiceAndWorkflowBeanIsCoveredByTheBoundaryContractTest() {
    assertThat(scanServicesAndWorkflows()).containsExactlyInAnyOrder(APPLICATION_BOUNDARIES);
  }

  @Test
  void workflowsAreComponentsAndDoNotDependOnWorkflows() {
    for (Class<?> workflow : APPLICATION_BOUNDARIES) {
      if (!workflow.getSimpleName().endsWith("Workflow")) {
        continue;
      }
      assertThat(workflow.isAnnotationPresent(Component.class)).isTrue();
      assertThat(workflow.isAnnotationPresent(Service.class)).isFalse();
      assertThat(
              Arrays.stream(workflow.getDeclaredFields())
                  .map(field -> field.getType().getSimpleName())
                  .noneMatch(name -> name.endsWith("Workflow")))
          .as(workflow.getName())
          .isTrue();
    }
  }

  @Test
  void workflowsThatCoordinateLocalMutationsOwnTransactions() {
    assertThat(
            List.of(
                    AcceptInvitationWorkflow.class,
                    CreateInvitationWorkflow.class,
                    RevokeInvitationWorkflow.class)
                .stream()
                .allMatch(
                    workflow ->
                        Arrays.stream(workflow.getDeclaredMethods()).anyMatch(this::transactional)))
        .isTrue();
    assertThat(
            Arrays.stream(ReconcileInvitationCompletionsWorkflow.class.getDeclaredMethods())
                .noneMatch(this::transactional))
        .isTrue();
  }

  @Test
  void ownerMutationsRequireTheWorkflowTransaction() throws Exception {
    assertThat(
            InvitationService.class
                .getMethod(
                    "create",
                    String.class,
                    io.github.lutzseverino.cardo.invite.model.CreateInvitationInput.class,
                    java.util.UUID.class,
                    String.class)
                .getAnnotation(Transactional.class)
                .propagation())
        .isEqualTo(Propagation.MANDATORY);
    assertThat(
            InvitationService.class
                .getMethod(
                    "accept",
                    java.util.UUID.class,
                    java.time.OffsetDateTime.class,
                    java.util.UUID.class)
                .getAnnotation(Transactional.class)
                .propagation())
        .isEqualTo(Propagation.MANDATORY);
  }

  @Test
  void revocationOwnerMutationRequiresTheWorkflowTransaction() throws Exception {
    assertThat(
            InvitationService.class
                .getMethod("revoke", java.util.UUID.class, String.class)
                .getAnnotation(Transactional.class)
                .propagation())
        .isEqualTo(Propagation.MANDATORY);
    assertThat(
            InvitationCompletionService.class
                .getMethod("revoke", java.util.UUID.class)
                .getAnnotation(Transactional.class)
                .propagation())
        .isEqualTo(Propagation.MANDATORY);
  }

  @Test
  void servicesDoNotDependOnWorkflowsAndControllersDoNotDependOnPersistence() {
    assertThat(
            Arrays.stream(InvitationService.class.getDeclaredFields())
                .map(field -> field.getType().getSimpleName())
                .noneMatch(name -> name.endsWith("Workflow")))
        .isTrue();
    scan(RestController.class, "io.github.lutzseverino.cardo.invite.controller")
        .forEach(this::assertNoPersistenceDependency);
  }

  @Test
  void applicationBoundariesDependOnPortsInsteadOfConcreteIntegrations() {
    Arrays.stream(APPLICATION_BOUNDARIES)
        .forEach(
            boundary ->
                assertThat(
                        Arrays.stream(boundary.getDeclaredFields())
                            .map(field -> field.getType().getPackageName())
                            .noneMatch(packageName -> packageName.contains(".integration")))
                    .as(boundary.getName())
                    .isTrue());
  }

  private boolean transactional(Method method) {
    return java.lang.reflect.Modifier.isPublic(method.getModifiers())
        && method.isAnnotationPresent(Transactional.class);
  }

  private List<Class<?>> scanServicesAndWorkflows() {
    Set<Class<?>> boundaries =
        new java.util.LinkedHashSet<>(
            scan(Service.class, "io.github.lutzseverino.cardo.invite.service"));
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*Workflow")));
    scanner.findCandidateComponents("io.github.lutzseverino.cardo.invite.workflow").stream()
        .map(definition -> load(definition.getBeanClassName()))
        .forEach(boundaries::add);
    return List.copyOf(boundaries);
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

  private void assertNoPersistenceDependency(Class<?> controller) {
    assertThat(
            Arrays.stream(controller.getDeclaredFields())
                .map(field -> field.getType().getSimpleName())
                .noneMatch(name -> name.endsWith("Repository") || name.endsWith("Resolver")))
        .as(controller.getName())
        .isTrue();
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
