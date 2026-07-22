package io.github.lutzseverino.cardo.authorization.grant;

import io.github.lutzseverino.cardo.authorization.AuthorizationAdminClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

@EnableScheduling
@Configuration(proxyBeanMethods = false)
public class AuthorizationPlanConfiguration {

  @Bean
  GrantReceiptStore grantReceiptStore(
      JdbcOperations jdbc, @Value("${spring.modulith.events.jdbc.schema}") String eventSchema) {
    return new GrantReceiptStore(jdbc, eventSchema);
  }

  @Bean
  GrantReceiptProcessingLock grantReceiptProcessingLock(JdbcOperations jdbc) {
    return new GrantReceiptProcessingLock(jdbc);
  }

  @Bean
  Grants grants(ApplicationEventPublisher events, GrantReceiptStore receipts) {
    return new Grants(events, receipts);
  }

  @Bean
  Revocations revocations(ApplicationEventPublisher events) {
    return new Revocations(events);
  }

  @Bean
  GrantProcessor grantProcessor(AuthorizationAdminClient authorization) {
    return new GrantProcessor(authorization);
  }

  @Bean
  GrantReceiptFailureRecorder grantReceiptFailureRecorder(
      GrantReceiptStore receipts, PlatformTransactionManager transactions) {
    return new GrantReceiptFailureRecorder(receipts, transactions);
  }

  @Bean
  GrantPlanListener grantPlanListener(
      GrantProcessor processor,
      GrantReceiptStore receipts,
      GrantReceiptProcessingLock processingLock,
      GrantReceiptFailureRecorder failures,
      AuthorizationWorkflowMetrics metrics,
      @Value("${cardo.authorization.plans.max-attempts:12}") int maxAttempts) {
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("authorization plan max attempts must be positive");
    }
    return new GrantPlanListener(
        processor, receipts, processingLock, failures, metrics, maxAttempts);
  }

  @Bean
  RevocationProcessor revocationProcessor(AuthorizationAdminClient authorization) {
    return new RevocationProcessor(authorization);
  }

  @Bean
  RevocationPlanListener revocationPlanListener(
      RevocationProcessor processor, AuthorizationWorkflowMetrics metrics) {
    return new RevocationPlanListener(processor, metrics);
  }

  @Bean
  AuthorizationPlanRecovery authorizationPlanRecovery(
      IncompleteEventPublications publications,
      @Value("${cardo.authorization.plans.retry-delay:PT1M}") Duration retryDelay) {
    return new AuthorizationPlanRecovery(publications, retryDelay);
  }

  @Bean
  AuthorizationWorkflowMetrics authorizationWorkflowMetrics(
      ObjectProvider<MeterRegistry> registries,
      JdbcOperations jdbc,
      @Value("${spring.modulith.events.jdbc.schema}") String eventSchema,
      @Value("${cardo.authorization.plans.retry-delay:PT1M}") Duration retryDelay) {
    return new AuthorizationWorkflowMetrics(
        registries.getIfAvailable(SimpleMeterRegistry::new), jdbc, eventSchema, retryDelay);
  }
}
