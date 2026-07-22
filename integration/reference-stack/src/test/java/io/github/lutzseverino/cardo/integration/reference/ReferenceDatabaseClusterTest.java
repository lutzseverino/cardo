package io.github.lutzseverino.cardo.integration.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReferenceDatabaseClusterTest {

  @Test
  void givesEachNonOwnerApplicationCreateOnlyInItsOwnDatabase() throws Exception {
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
                          + "has_database_privilege(current_user, current_database(), 'CREATE') "
                          + "from pg_database where datname = current_database()")) {
            assertThat(query.next()).isTrue();
            assertThat(query.getString(1))
                .isEqualTo(database.owner())
                .isNotEqualTo(database.application());
            assertThat(query.getBoolean(2)).isTrue();
          }
        }
        String foreignService = "identity".equals(service) ? "billing" : "identity";
        assertThatThrownBy(
                () ->
                    DriverManager.getConnection(
                        cluster.jdbcUrl(cluster.database(foreignService).name()),
                        database.application(),
                        database.password()))
            .isInstanceOf(SQLException.class);
      }
    }
  }
}
