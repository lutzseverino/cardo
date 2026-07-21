package io.github.lutzseverino.cardo.billing.config;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.lutzseverino.cardo.authorization.spring.AuthenticatedUserReader;
import io.github.lutzseverino.cardo.authorization.spring.CardoJwtClaims;
import io.github.lutzseverino.cardo.billing.BillingPermissions;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(BillingSecurityTrafficTest.TrafficController.class)
@Import({SecurityConfig.class, BillingSecurityTrafficTest.TrafficController.class})
@ImportAutoConfiguration({
  SecurityAutoConfiguration.class,
  ServletWebSecurityAutoConfiguration.class,
  SecurityFilterAutoConfiguration.class
})
@TestPropertySource(
    properties = {
      "cardo.api.base-path=/api/v1",
      "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://identity.example/realms/cardo"
    })
class BillingSecurityTrafficTest {

  private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Autowired private MockMvc mvc;
  @MockitoBean private JwtDecoder decoder;

  @BeforeEach
  void tokens() {
    when(decoder.decode("user-token"))
        .thenReturn(
            jwt("user-token").claim(CardoJwtClaims.IDENTITY_USER_ID, USER_ID.toString()).build());
    when(decoder.decode("service-token"))
        .thenReturn(
            jwt("service-token")
                .claim(
                    "resource_access",
                    Map.of("billing", Map.of("roles", List.of("entitlement:read"))))
                .build());
  }

  @Test
  void keepsPublicAndProtectedRoutesDistinct() throws Exception {
    mvc.perform(get("/api/v1/billing")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/traffic/user")).andExpect(status().isUnauthorized());
  }

  @Test
  void acceptsUserAndServiceTokensOnlyAtTheirExistingBoundaries() throws Exception {
    mvc.perform(get("/api/v1/traffic/user").header("Authorization", "Bearer user-token"))
        .andExpect(status().isOk())
        .andExpect(content().string(USER_ID.toString()));
    mvc.perform(get("/api/v1/traffic/user").header("Authorization", "Bearer service-token"))
        .andExpect(status().isUnauthorized());

    mvc.perform(get("/api/v1/traffic/service").header("Authorization", "Bearer service-token"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/traffic/service").header("Authorization", "Bearer user-token"))
        .andExpect(status().isForbidden());
  }

  private Jwt.Builder jwt(String value) {
    return Jwt.withTokenValue(value)
        .header("alg", "RS256")
        .subject("caller")
        .issuedAt(Instant.now().minusSeconds(5))
        .expiresAt(Instant.now().plusSeconds(300))
        .audience(List.of("billing"));
  }

  @RestController
  public static class TrafficController {

    private final AuthenticatedUserReader users;

    TrafficController(AuthenticatedUserReader users) {
      this.users = users;
    }

    @GetMapping("/api/v1/billing")
    String publicStatus() {
      return "ok";
    }

    @GetMapping("/api/v1/traffic/user")
    String user() {
      return users.currentUser().id().toString();
    }

    @PreAuthorize("hasAuthority('" + BillingPermissions.ENTITLEMENT_READ_AUTHORITY + "')")
    @GetMapping("/api/v1/traffic/service")
    String service() {
      return "ok";
    }
  }
}
