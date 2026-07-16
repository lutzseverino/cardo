package io.github.lutzseverino.cardo.identity.reader;

import io.github.lutzseverino.cardo.identity.model.AuthenticatedPrincipal;
import io.github.lutzseverino.cardo.identity.model.AuthenticationMethod;
import io.github.lutzseverino.cardo.identity.model.UserStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthenticatedPrincipalReader {

  private final JdbcTemplate jdbc;

  AuthenticatedPrincipalReader(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<AuthenticatedPrincipal> findByKeycloakSubject(
      String keycloakSubject,
      String sessionId,
      AuthenticationMethod authenticationMethod,
      OffsetDateTime expiresAt) {
    return jdbc
        .query(
            """
            select
              u.id as user_id,
              u.keycloak_subject,
              u.email as user_email,
              u.name as user_name,
              u.avatar_url as user_avatar_url,
              u.status as user_status,
              u.email_verified as user_email_verified,
              u.created_at as user_created_at,
              u.updated_at as user_updated_at
            from users u
            where u.keycloak_subject = ?
            """,
            (rs, rowNum) -> map(rs, sessionId, authenticationMethod, expiresAt),
            keycloakSubject)
        .stream()
        .findFirst();
  }

  private AuthenticatedPrincipal map(
      ResultSet rs,
      String sessionId,
      AuthenticationMethod authenticationMethod,
      OffsetDateTime expiresAt)
      throws SQLException {
    return new AuthenticatedPrincipal(
        sessionId,
        rs.getObject("user_id", UUID.class),
        rs.getString("keycloak_subject"),
        rs.getString("user_email"),
        rs.getString("user_name"),
        rs.getString("user_avatar_url"),
        UserStatus.valueOf(rs.getString("user_status")),
        rs.getBoolean("user_email_verified"),
        rs.getObject("user_created_at", OffsetDateTime.class),
        rs.getObject("user_updated_at", OffsetDateTime.class),
        authenticationMethod,
        null,
        expiresAt);
  }
}
