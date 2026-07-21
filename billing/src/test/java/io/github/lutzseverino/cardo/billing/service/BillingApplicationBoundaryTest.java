package io.github.lutzseverino.cardo.billing.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lutzseverino.cardo.billing.BillingApplication;
import io.github.lutzseverino.cardo.billing.BillingPermissions;
import io.github.lutzseverino.cardo.billing.workflow.CreateCheckoutSessionWorkflow;
import io.github.lutzseverino.cardo.billing.workflow.CreatePortalSessionWorkflow;
import io.github.lutzseverino.cardo.billing.workflow.ProcessStripeWebhookWorkflow;
import jakarta.persistence.Entity;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

class BillingApplicationBoundaryTest {

  private static final Class<?>[] APPLICATION_BOUNDARIES = {
    CustomerService.class,
    CustomerProvisioningService.class,
    EntitlementService.class,
    CreateCheckoutSessionWorkflow.class,
    CreatePortalSessionWorkflow.class,
    ProcessStripeWebhookWorkflow.class
  };

  @Test
  void applicationContractsExcludeTransportPersistenceEntitiesAndProviderSdks() {
    assertApplicationBoundary(APPLICATION_BOUNDARIES);
  }

  @Test
  void everyServiceAndWorkflowBeanIsCoveredByTheBoundaryContractTest() {
    assertThat(scanServicesAndWorkflows()).containsExactlyInAnyOrder(APPLICATION_BOUNDARIES);
  }

  @Test
  void workflowOwnsTheWebhookTransaction() throws Exception {
    assertThat(ProcessStripeWebhookWorkflow.class.isAnnotationPresent(Component.class)).isTrue();
    assertThat(ProcessStripeWebhookWorkflow.class.isAnnotationPresent(Service.class)).isFalse();
    assertThat(
            method(ProcessStripeWebhookWorkflow.class, "process", String.class, String.class)
                .isAnnotationPresent(Transactional.class))
        .isTrue();
    assertThat(
            Arrays.stream(ProcessStripeWebhookWorkflow.class.getDeclaredFields())
                .map(field -> field.getType().getSimpleName())
                .noneMatch(name -> name.endsWith("Workflow")))
        .isTrue();
  }

  @Test
  void customerProvisioningKeepsProviderCallsOutsideTransactions() {
    Class<?> provisioner =
        load("io.github.lutzseverino.cardo.billing.workflow.CustomerProvisioner");
    assertThat(provisioner.isAnnotationPresent(Transactional.class)).isFalse();
    assertThat(Arrays.stream(provisioner.getDeclaredMethods()))
        .noneMatch(method -> method.isAnnotationPresent(Transactional.class));
    assertThat(
            Arrays.stream(CustomerProvisioningService.class.getDeclaredFields())
                .map(field -> field.getType().getSimpleName()))
        .doesNotContain("BillingProvider");
  }

  @Test
  void billingEnablesTheProvisioningRecoverySchedule() {
    assertThat(BillingApplication.class.isAnnotationPresent(EnableScheduling.class)).isTrue();
  }

  @Test
  void workflowsUseComponentsAndDependOnPortsInsteadOfConcreteIntegrations() {
    List.of(
            CreateCheckoutSessionWorkflow.class,
            CreatePortalSessionWorkflow.class,
            ProcessStripeWebhookWorkflow.class)
        .forEach(
            workflow -> {
              assertThat(workflow.isAnnotationPresent(Component.class))
                  .as(workflow.getName())
                  .isTrue();
              assertThat(workflow.isAnnotationPresent(Service.class))
                  .as(workflow.getName())
                  .isFalse();
              assertThat(
                      Arrays.stream(workflow.getDeclaredFields())
                          .map(field -> field.getType().getPackageName())
                          .noneMatch(packageName -> packageName.contains(".integration")))
                  .as(workflow.getName())
                  .isTrue();
            });
  }

  @Test
  void serviceAndControllerDependenciesPointInward() {
    Arrays.stream(APPLICATION_BOUNDARIES)
        .filter(boundary -> boundary.getSimpleName().endsWith("Service"))
        .forEach(
            service ->
                assertThat(
                        Arrays.stream(service.getDeclaredFields())
                            .map(field -> field.getType().getSimpleName())
                            .noneMatch(name -> name.endsWith("Workflow")))
                    .as(service.getName())
                    .isTrue());
    scan(RestController.class, "io.github.lutzseverino.cardo.billing.controller")
        .forEach(this::assertNoPersistenceDependency);
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
  void ownerMutationsRemainTransactional() throws Exception {
    assertThat(
            method(CustomerProvisioningService.class, "request", UUID.class, String.class)
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

  private void assertApplicationBoundary(Class<?>... boundaries) {
    Arrays.stream(boundaries)
        .flatMap(boundary -> Arrays.stream(boundary.getDeclaredMethods()))
        .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
        .forEach(
            method -> {
              assertThat(forbidden(method.getGenericReturnType())).as(method.toString()).isFalse();
              Arrays.stream(method.getGenericParameterTypes())
                  .forEach(type -> assertThat(forbidden(type)).as(method.toString()).isFalse());
            });
  }

  private List<Class<?>> scanServicesAndWorkflows() {
    Set<Class<?>> boundaries =
        new java.util.LinkedHashSet<>(
            scan(Service.class, "io.github.lutzseverino.cardo.billing.service"));
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*Workflow")));
    scanner.findCandidateComponents("io.github.lutzseverino.cardo.billing.workflow").stream()
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
