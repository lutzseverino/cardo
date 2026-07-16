package io.github.lutzseverino.cardo.authorization.schema;

import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AuthorizationSchemaConfiguration {

  static final String MIGRATION_LOCATION = "classpath:db/authorization/access";
  static final String HISTORY_TABLE = "flyway_schema_history_authorization";

  @Bean
  FlywayMigrationStrategy authorizationSchemaMigrationStrategy() {
    return serviceFlyway -> {
      authorizationFlyway(serviceFlyway).migrate();
      serviceFlyway.migrate();
    };
  }

  static Flyway authorizationFlyway(Flyway serviceFlyway) {
    return Flyway.configure()
        .configuration(serviceFlyway.getConfiguration())
        .locations(MIGRATION_LOCATION)
        .table(HISTORY_TABLE)
        .baselineOnMigrate(true)
        .baselineVersion("0")
        .load();
  }
}
