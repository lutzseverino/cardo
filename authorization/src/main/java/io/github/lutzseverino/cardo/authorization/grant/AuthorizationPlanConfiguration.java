package io.github.lutzseverino.cardo.authorization.grant;

import io.github.lutzseverino.cardo.authorization.AuthorizationAdminClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@Configuration(proxyBeanMethods = false)
public class AuthorizationPlanConfiguration {

  @Bean
  Grants grants(ApplicationEventPublisher events) {
    return new Grants(events);
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
  GrantPlanListener grantPlanListener(GrantProcessor processor) {
    return new GrantPlanListener(processor);
  }

  @Bean
  RevocationProcessor revocationProcessor(AuthorizationAdminClient authorization) {
    return new RevocationProcessor(authorization);
  }

  @Bean
  RevocationPlanListener revocationPlanListener(RevocationProcessor processor) {
    return new RevocationPlanListener(processor);
  }

  @Bean
  AuthorizationPlanRecovery authorizationPlanRecovery(
      IncompleteEventPublications publications,
      @Value("${cardo.authorization.plans.retry-delay:PT1M}") Duration retryDelay) {
    return new AuthorizationPlanRecovery(publications, retryDelay);
  }
}
