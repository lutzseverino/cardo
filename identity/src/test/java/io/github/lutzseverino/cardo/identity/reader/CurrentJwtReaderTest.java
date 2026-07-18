package io.github.lutzseverino.cardo.identity.reader;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lutzseverino.cardo.authorization.grant.EffectiveGrantAuthorityReader;
import io.github.lutzseverino.cardo.authorization.grant.GrantedResource;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CurrentJwtReaderTest {

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void extractsApplicationAuthenticationContextAtTheInboundBoundary() {
    Instant expiresAt = Instant.parse("2030-01-01T00:00:00Z");
    Jwt jwt =
        Jwt.withTokenValue("access-token")
            .header("alg", "none")
            .subject("subject-1")
            .claim("sid", "session-1")
            .expiresAt(expiresAt)
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("clinic:clinic:tenant-1:read")),
                "subject-1"));

    var current = new CurrentJwtReader(new EffectiveGrantAuthorityReader()).current();

    assertThat(current.authorizationSubject()).isEqualTo("subject-1");
    assertThat(current.sessionId()).isEqualTo("session-1");
    assertThat(current.expiresAt()).isEqualTo(OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
    assertThat(current.grants())
        .singleElement()
        .satisfies(
            grant -> {
              assertThat(grant.resource())
                  .isEqualTo(new GrantedResource("clinic:clinic", "tenant-1"));
              assertThat(grant.actions()).containsExactly("read");
            });
  }
}
