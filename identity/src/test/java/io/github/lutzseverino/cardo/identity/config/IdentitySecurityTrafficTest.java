package io.github.lutzseverino.cardo.identity.config;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(IdentitySecurityTrafficTest.TrafficController.class)
@Import({
  SecurityConfig.class,
  IdentitySecurityTrafficTest.TrafficController.class,
  IdentitySecurityTrafficTest.Properties.class
})
@ImportAutoConfiguration({
  SecurityAutoConfiguration.class,
  ServletWebSecurityAutoConfiguration.class,
  SecurityFilterAutoConfiguration.class
})
@TestPropertySource(
    properties = {
      "cardo.api.base-path=/api/v1",
      "cardo.identity.session.mode=local",
      "cardo.identity.session.access-cookie-name=cardo.session",
      "cardo.identity.session.refresh-cookie-name=cardo.refresh",
      "cardo.identity.session.csrf-cookie-name=cardo.csrf",
      "cardo.identity.session.refresh-cookie-path=/api/v1/identity/sessions/current",
      "cardo.identity.session.secure=false",
      "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://identity.example/realms/cardo"
    })
class IdentitySecurityTrafficTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private JwtDecoder decoder;

  @Test
  void keepsStatusPublicAndProtectedIdentityRoutesAuthenticated() throws Exception {
    mvc.perform(get("/api/v1/identity")).andExpect(status().isOk());

    mvc.perform(get("/api/v1/identity/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
        .andExpect(content().string(""));
  }

  @Test
  void keepsBrowserSessionMutationsCsrfProtectedWithoutInventingAnErrorEnvelope() throws Exception {
    mvc.perform(post("/api/v1/identity/sessions"))
        .andExpect(status().isForbidden())
        .andExpect(content().string(""));
  }

  @RestController
  public static class TrafficController {

    @GetMapping("/api/v1/identity")
    String publicStatus() {
      return "ok";
    }

    @GetMapping("/api/v1/identity/users/me")
    String protectedUser() {
      return "protected";
    }

    @PostMapping("/api/v1/identity/sessions")
    String createSession() {
      return "session";
    }
  }

  @EnableConfigurationProperties(SessionProperties.class)
  static class Properties {}
}
