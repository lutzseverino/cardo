package io.github.lutzseverino.cardo.integration.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReferenceDatabaseClusterTest {

  @Test
  void givesEachApplicationOnlyItsOwnDatabase() throws Exception {
    try (ReferenceDatabaseCluster cluster = new ReferenceDatabaseCluster()) {
      cluster.start();
      for (String service : List.of("identity", "invite", "billing", "product")) {
        ReferenceDatabaseCluster.Database database = cluster.database(service);
        try (var connection =
            DriverManager.getConnection(
                cluster.jdbcUrl(database.name()), database.application(), database.password())) {
          try (var query = connection.createStatement().executeQuery("select current_user")) {
            assertThat(query.next()).isTrue();
            assertThat(query.getString(1)).isEqualTo(database.application());
          }
          try (var query =
              connection
                  .createStatement()
                  .executeQuery(
                      "select pg_catalog.pg_get_userbyid(datdba), "
                          + "has_database_privilege(current_user, current_database(), 'CREATE'), "
                          + "exists(select 1 from pg_extension where extname = 'pgcrypto') "
                          + "from pg_database where datname = current_database()")) {
            assertThat(query.next()).isTrue();
            assertThat(query.getString(1)).isEqualTo(database.owner());
            assertThat(query.getBoolean(2)).isFalse();
            assertThat(query.getBoolean(3)).isTrue();
          }
          assertThatThrownBy(() -> connection.createStatement().execute("create extension hstore"))
              .isInstanceOf(SQLException.class);
          if (database.authorizationSchema() == null) {
            assertThat(service).isEqualTo("billing");
          } else {
            try (var query =
                connection
                    .createStatement()
                    .executeQuery(
                        "select schema_owner, "
                            + "has_schema_privilege(current_user, '"
                            + database.authorizationSchema()
                            + "', 'USAGE'), "
                            + "has_schema_privilege(current_user, '"
                            + database.authorizationSchema()
                            + "', 'CREATE') "
                            + "from information_schema.schemata where schema_name = '"
                            + database.authorizationSchema()
                            + "'")) {
              assertThat(query.next()).isTrue();
              assertThat(query.getString(1)).isEqualTo(database.owner());
              assertThat(query.getBoolean(2)).isTrue();
              assertThat(query.getBoolean(3)).isTrue();
              assertThat(query.next()).isFalse();
            }
          }
        }
      }
      ReferenceDatabaseCluster.Database identity = cluster.database("identity");
      assertThatThrownBy(
              () ->
                  DriverManager.getConnection(
                      cluster.jdbcUrl(cluster.database("billing").name()),
                      identity.application(),
                      identity.password()))
          .isInstanceOf(SQLException.class);
    }
  }
}
