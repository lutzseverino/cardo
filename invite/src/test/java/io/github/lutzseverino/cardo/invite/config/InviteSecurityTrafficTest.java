package io.github.lutzseverino.cardo.invite.config;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.lutzseverino.cardo.authorization.spring.CardoJwtClaims;
import io.github.lutzseverino.cardo.invite.reader.ProductCallerReader;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(InviteSecurityTrafficTest.TrafficController.class)
@Import({
  SecurityConfig.class,
  ProductCallerReader.class,
  InviteSecurityTrafficTest.TrafficController.class
})
@ImportAutoConfiguration({
  SecurityAutoConfiguration.class,
  ServletWebSecurityAutoConfiguration.class,
  SecurityFilterAutoConfiguration.class
})
@TestPropertySource(
    properties = {
      "cardo.api.base-path=/api/v1",
      "cardo.invite.keycloak.base-url=https://identity.example",
      "cardo.invite.keycloak.realm=cardo",
      "cardo.invite.keycloak.client-id=cardo-invite",
      "cardo.invite.keycloak.client-secret=secret",
      "cardo.invite.product-callers.allowed-client-ids=polity",
      "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://identity.example/realms/cardo"
    })
class InviteSecurityTrafficTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private JwtDecoder decoder;

  @BeforeEach
  void tokens() {
    when(decoder.decode("service-token")).thenReturn(serviceToken("polity", true));
    when(decoder.decode("unknown-service-token")).thenReturn(serviceToken("unknown", true));
    when(decoder.decode("roleless-service-token")).thenReturn(serviceToken("polity", false));
    when(decoder.decode("user-token"))
        .thenReturn(
            token("user-token")
                .claim("azp", "polity")
                .claim(CardoJwtClaims.IDENTITY_USER_ID, UUID.randomUUID().toString())
                .claim(
                    "resource_access",
                    Map.of("cardo-invite", Map.of("roles", List.of("product-service"))))
                .build());
  }

  @Test
  void keepsStatusAndInvitationInspectionPublic() throws Exception {
    mvc.perform(get("/api/v1/invite")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/invitation-tokens/secret"))
        .andExpect(status().isOk())
        .andExpect(content().string("public"));
    mvc.perform(post("/api/v1/invitations")).andExpect(status().isUnauthorized());
  }

  @Test
  void preservesServiceRoleAllowlistAndUserTokenRejection() throws Exception {
    mvc.perform(post("/api/v1/invitations").header("Authorization", "Bearer service-token"))
        .andExpect(status().isOk())
        .andExpect(content().string("polity"));
    mvc.perform(post("/api/v1/invitations").header("Authorization", "Bearer user-token"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/invitations").header("Authorization", "Bearer roleless-service-token"))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/invitations").header("Authorization", "Bearer unknown-service-token"))
        .andExpect(status().isUnauthorized());
  }

  private Jwt serviceToken(String caller, boolean withRole) {
    Jwt.Builder token = token(caller + "-token").claim("azp", caller);
    if (withRole) {
      token.claim(
          "resource_access", Map.of("cardo-invite", Map.of("roles", List.of("product-service"))));
    }
    return token.build();
  }

  private Jwt.Builder token(String value) {
    return Jwt.withTokenValue(value)
        .header("alg", "RS256")
        .subject("caller")
        .issuedAt(Instant.now().minusSeconds(5))
        .expiresAt(Instant.now().plusSeconds(300))
        .audience(List.of("cardo-invite"));
  }

  @RestController
  public static class TrafficController {

    private final ProductCallerReader callers;

    TrafficController(ProductCallerReader callers) {
      this.callers = callers;
    }

    @GetMapping("/api/v1/invite")
    String publicStatus() {
      return "ok";
    }

    @GetMapping("/api/v1/invitation-tokens/{token}")
    String publicInvitation() {
      return "public";
    }

    @PostMapping("/api/v1/invitations")
    String service() {
      return callers.currentProduct();
    }
  }
}
