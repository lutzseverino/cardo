package io.github.lutzseverino.cardo.identity.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lutzseverino.cardo.identity.config.BrowserSessionProperties;
import io.github.lutzseverino.cardo.identity.service.BrowserSessionService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DataJpaTest(
    properties = {
      "spring.flyway.locations=classpath:db/migration",
      "spring.jpa.hibernate.ddl-auto=validate",
      "cardo.identity.browser-session.idle-timeout=30m",
      "cardo.identity.browser-session.absolute-timeout=8h",
      "cardo.identity.browser-session.refresh-lease=30s",
      "cardo.identity.browser-session.retention=7d",
      "cardo.identity.browser-session.cleanup-delay=1h"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({BrowserSessionService.class, BrowserSessionPostgreSqlIntegrationTest.Config.class})
@EnableConfigurationProperties(BrowserSessionProperties.class)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BrowserSessionPostgreSqlIntegrationTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2030-01-01T00:00:00Z");

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.5-alpine")
          .withDatabaseName("identity_sessions")
          .withUsername("identity")
          .withPassword("identity");

  private final BrowserSessionRepository sessions;
  private final BrowserSessionService service;
  private final JdbcTemplate jdbc;

  @Autowired
  BrowserSessionPostgreSqlIntegrationTest(
      BrowserSessionRepository sessions, BrowserSessionService service, JdbcTemplate jdbc) {
    this.sessions = sessions;
    this.service = service;
    this.jdbc = jdbc;
  }

  @DynamicPropertySource
  static void configurePostgres(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    properties.add("spring.datasource.username", POSTGRES::getUsername);
    properties.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @BeforeEach
  void clearDatabase() {
    sessions.deleteAll();
  }

  @Test
  void migrationPersistsOnlyCredentialFingerprintsAndSupportsRotation() {
    var created = service.start("session-1", "subject-1", "refresh-secret-1", NOW.plusHours(8));

    assertThat(created.sessionStartedAt()).isEqualTo(NOW);
    assertThat(created.idleExpiresAt()).isEqualTo(NOW.plusMinutes(30));
    assertThat(
            jdbc.queryForObject(
                "select refresh_credential_hash from browser_sessions where provider_session_id = ?",
                String.class,
                "session-1"))
        .hasSize(64)
        .doesNotContain("refresh-secret");

    var refresh = service.claimRefresh("refresh-secret-1");
    service.completeRefresh(
        refresh, "session-1", "subject-1", "refresh-secret-2", NOW.plusHours(8));

    assertThat(service.claimRefresh("refresh-secret-2").providerSessionId()).isEqualTo("session-1");
    assertThat(
            jdbc.queryForList(
                "select version from flyway_schema_history_identity where success order by installed_rank",
                String.class))
        .containsExactly("1", "2", "3", "4", "5", "6", "7");
  }

  @TestConfiguration
  static class Config {

    @Bean
    Clock clock() {
      return Clock.fixed(NOW.toInstant(), ZoneOffset.UTC);
    }
  }
}
