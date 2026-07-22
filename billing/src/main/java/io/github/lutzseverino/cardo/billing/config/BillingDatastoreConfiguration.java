package io.github.lutzseverino.cardo.billing.config;

import io.github.lutzseverino.cardo.common.runtime.DatastoreOwnershipVerifier;
import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

@Configuration(proxyBeanMethods = false)
class BillingDatastoreConfiguration {

  @Bean
  @DependsOn("flywayInitializer")
  InitializingBean billingDatastoreOwnershipPolicy(
      BillingRuntimeProperties runtime,
      BillingDatastoreProperties datastore,
      DataSource dataSource) {
    return () -> {
      if (runtime.mode() == BillingRuntimeProperties.Mode.PRODUCTION) {
        DatastoreOwnershipVerifier.verify(
            dataSource,
            "cardo.billing.datastore",
            datastore.databaseName(),
            datastore.ownerRole(),
            datastore.applicationRole());
      }
    };
  }
}
