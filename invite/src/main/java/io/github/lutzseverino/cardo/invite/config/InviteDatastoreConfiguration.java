package io.github.lutzseverino.cardo.invite.config;

import io.github.lutzseverino.cardo.common.runtime.DatastoreOwnershipVerifier;
import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

@Configuration(proxyBeanMethods = false)
class InviteDatastoreConfiguration {

  @Bean
  @DependsOn("flywayInitializer")
  InitializingBean inviteDatastoreOwnershipPolicy(
      InviteRuntimeProperties runtime, InviteDatastoreProperties datastore, DataSource dataSource) {
    return () -> {
      if (runtime.mode() == InviteRuntimeProperties.Mode.PRODUCTION) {
        DatastoreOwnershipVerifier.verify(
            dataSource,
            "cardo.invite.datastore",
            datastore.databaseName(),
            datastore.ownerRole(),
            datastore.applicationRole());
      }
    };
  }
}
