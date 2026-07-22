package io.github.lutzseverino.cardo.identity.config;

import io.github.lutzseverino.cardo.common.runtime.DatastoreOwnershipVerifier;
import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

@Configuration(proxyBeanMethods = false)
class IdentityDatastoreConfiguration {

  @Bean
  @DependsOn("flywayInitializer")
  InitializingBean identityDatastoreOwnershipPolicy(
      IdentityRuntimeProperties runtime,
      IdentityDatastoreProperties datastore,
      DataSource dataSource) {
    return () -> {
      if (runtime.mode() == IdentityRuntimeProperties.Mode.PRODUCTION) {
        DatastoreOwnershipVerifier.verify(
            dataSource,
            "cardo.identity.datastore",
            datastore.databaseName(),
            datastore.ownerRole(),
            datastore.applicationRole());
      }
    };
  }
}
