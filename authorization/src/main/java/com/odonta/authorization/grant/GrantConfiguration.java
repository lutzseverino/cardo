package com.odonta.authorization.grant;

import com.odonta.authorization.AuthorizationAdminClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@Configuration(proxyBeanMethods = false)
public class GrantConfiguration {

  @Bean
  Grants grants(ApplicationEventPublisher events) {
    return new Grants(events);
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
  GrantRecovery grantRecovery(FailedEventPublications publications) {
    return new GrantRecovery(publications);
  }
}
