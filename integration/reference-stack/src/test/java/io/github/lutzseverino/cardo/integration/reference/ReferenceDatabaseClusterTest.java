package io.github.lutzseverino.cardo.integration.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class ReferenceDatabaseClusterTest {

  @Test
  void givesEachApplicationOnlyItsOwnDatabase() throws Exception {
    try (ReferenceDatabaseCluster cluster = new ReferenceDatabaseCluster()) {
      cluster.start();
      ReferenceDatabaseCluster.Database identity = cluster.database("identity");
      try (var connection =
          DriverManager.getConnection(
              cluster.jdbcUrl(identity.name()), identity.application(), identity.password())) {
        try (var query = connection.createStatement().executeQuery("select current_user")) {
          assertThat(query.next()).isTrue();
          assertThat(query.getString(1)).isEqualTo(identity.application());
        }
        try (var query =
            connection
                .createStatement()
                .executeQuery(
                    "select pg_catalog.pg_get_userbyid(datdba) from pg_database where datname = current_database()")) {
          assertThat(query.next()).isTrue();
          assertThat(query.getString(1)).isEqualTo(identity.owner());
        }
      }
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
