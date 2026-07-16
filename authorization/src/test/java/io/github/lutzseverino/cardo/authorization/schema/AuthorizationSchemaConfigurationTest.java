package io.github.lutzseverino.cardo.authorization.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class AuthorizationSchemaConfigurationTest {

  @Test
  void createsDedicatedAuthorizationFlyway() {
    DataSource dataSource = mock(DataSource.class);
    Flyway serviceFlyway = mock(Flyway.class);
    Flyway configuredServiceFlyway =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .table("flyway_schema_history_clinic")
            .load();
    when(serviceFlyway.getConfiguration()).thenReturn(configuredServiceFlyway.getConfiguration());

    Flyway authorizationFlyway =
        AuthorizationSchemaConfiguration.authorizationFlyway(serviceFlyway);

    assertThat(authorizationFlyway.getConfiguration().getDataSource()).isSameAs(dataSource);
    assertThat(authorizationFlyway.getConfiguration().getTable())
        .isEqualTo(AuthorizationSchemaConfiguration.HISTORY_TABLE);
    assertThat(authorizationFlyway.getConfiguration().isBaselineOnMigrate()).isTrue();
    assertThat(authorizationFlyway.getConfiguration().getBaselineVersion().getVersion())
        .isEqualTo("0");
    assertThat(authorizationFlyway.getConfiguration().getLocations())
        .extracting(Object::toString)
        .containsExactly(AuthorizationSchemaConfiguration.MIGRATION_LOCATION);
  }
}
