package io.github.lutzseverino.cardo.integration.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import io.github.lutzseverino.cardo.authorization.spring.CardoJwtClaims;
import io.github.lutzseverino.cardo.identity.productauth.IdentityProductAuthProperties;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

class ReferenceIdentitySessionSecurityTest {

  private HttpServer issuerServer;
  private KeyPair keys;
  private String issuer;

  @BeforeEach
  void startIssuer() throws Exception {
    keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    issuerServer = HttpServer.create(new InetSocketAddress(0), 0);
    issuer = "http://localhost:" + issuerServer.getAddress().getPort() + "/realms/cardo";
    RSAKey publicKey =
        new RSAKey.Builder((RSAPublicKey) keys.getPublic()).keyID("reference-test-key").build();
    issuerServer.createContext(
        "/realms/cardo/certs", exchange -> respond(exchange, new JWKSet(publicKey).toString()));
    issuerServer.start();
  }

  @AfterEach
  void stopIssuer() {
    issuerServer.stop(0);
  }

  @Test
  void selectsOnlyTheExactAcceptanceAndConvergenceMethodsAndPaths() {
    assertThat(workflow("POST", "/api/reference/invitations/one/accept")).isTrue();
    assertThat(workflow("GET", "/api/reference/convergence/one")).isTrue();

    assertThat(workflow("GET", "/api/reference/invitations/one/accept")).isFalse();
    assertThat(workflow("POST", "/api/reference/invitations")).isFalse();
    assertThat(workflow("GET", "/api/reference/tenants/one")).isFalse();
    assertThat(workflow("GET", "/api/reference/billing/one")).isFalse();
  }

  @Test
  void resolvesOnlyAnUnopposedIdentitySessionCookie() {
    var resolver = new ReferenceIdentitySessionCookieResolver("cardo.session");
    MockHttpServletRequest cookie = request("POST", "/api/reference/invitations/one/accept");
    cookie.setCookies(new Cookie("cardo.session", "identity-session"));
    assertThat(resolver.resolve(cookie)).isEqualTo("identity-session");

    cookie.addHeader(HttpHeaders.AUTHORIZATION, "Bearer identity-session");
    assertThatThrownBy(() -> resolver.resolve(cookie))
        .isInstanceOf(OAuth2AuthenticationException.class);
    assertThat(resolver.resolve(new MockHttpServletRequest())).isNull();
  }

  @Test
  void authenticatesBeforeCsrfWithoutExemptingIdentitySessionCookies() throws Exception {
    webContext()
        .run(
            application -> {
              assertThat(application).hasNotFailed();
              SecurityFilterChain chain =
                  application.getBean(
                      "referenceIdentityWorkflowSecurity", SecurityFilterChain.class);
              assertThat(indexOf(chain, ReferenceIdentitySessionAuthenticationFilter.class))
                  .isLessThan(indexOf(chain, CsrfFilter.class));
              var mvc =
                  MockMvcBuilders.webAppContextSetup((WebApplicationContext) application)
                      .addFilters(application.getBean(FilterChainProxy.class))
                      .build();
              String valid = token(true);
              Cookie session = new Cookie("cardo.session", valid);
              Cookie csrf = new Cookie("cardo.csrf", "csrf-value");

              mvc.perform(
                      post("/api/reference/invitations/one/accept")
                          .cookie(session, csrf)
                          .header("X-CSRF-TOKEN", "csrf-value"))
                  .andExpect(status().isOk());
              mvc.perform(post("/api/reference/invitations/one/accept").cookie(session, csrf))
                  .andExpect(status().isForbidden());
              mvc.perform(
                      post("/api/reference/invitations/one/accept")
                          .cookie(session, csrf)
                          .header("X-CSRF-TOKEN", "wrong"))
                  .andExpect(status().isForbidden());
              mvc.perform(
                      post("/api/reference/invitations/one/accept")
                          .cookie(session, csrf)
                          .header("X-CSRF-TOKEN", "csrf-value")
                          .header(HttpHeaders.AUTHORIZATION, "Bearer " + valid))
                  .andExpect(status().isUnauthorized());
              mvc.perform(
                      post("/api/reference/invitations/one/accept")
                          .cookie(new Cookie("cardo.session", token(false)), csrf)
                          .header("X-CSRF-TOKEN", "csrf-value"))
                  .andExpect(status().isUnauthorized());
            });
  }

  @Test
  void keepsCsrfReadOnlyAndAcceptsOnlyTheExactHeaderValue() {
    var repository = new ReferenceReadOnlyCsrfTokenRepository("cardo.csrf");
    MockHttpServletRequest request = request("POST", "/api/reference/invitations/one/accept");
    request.setCookies(new Cookie("cardo.csrf", "csrf-value"));
    var token = repository.loadToken(request);
    assertThat(token).isNotNull();
    request.addHeader("X-CSRF-TOKEN", "csrf-value");
    assertThat(
            new ReferenceHeaderOnlyCsrfTokenRequestHandler().resolveCsrfTokenValue(request, token))
        .isEqualTo("csrf-value");

    MockHttpServletResponse response = new MockHttpServletResponse();
    repository.saveToken(
        new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "replacement"), request, response);
    assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
  }

  @Test
  void requiresAValidCardoIdentityUserClaim() {
    assertThat(
            ReferenceIdentitySessionSecurity.validIdentityUser(jwt(UUID.randomUUID().toString()))
                .hasErrors())
        .isFalse();
    assertThat(ReferenceIdentitySessionSecurity.validIdentityUser(jwt(null)).hasErrors()).isTrue();
    assertThat(ReferenceIdentitySessionSecurity.validIdentityUser(jwt("not-a-uuid")).hasErrors())
        .isTrue();
  }

  private boolean workflow(String method, String path) {
    return ReferenceIdentitySessionSecurity.workflowRequests().matches(request(method, path));
  }

  private MockHttpServletRequest request(String method, String path) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, path);
    request.setServletPath(path);
    return request;
  }

  private Jwt jwt(String userId) {
    Jwt.Builder jwt =
        Jwt.withTokenValue("identity-session").header("alg", "RS256").subject("subject");
    if (userId != null) {
      jwt.claim(CardoJwtClaims.IDENTITY_USER_ID, userId);
    }
    return jwt.build();
  }

  private WebApplicationContextRunner webContext() {
    IdentityProductAuthProperties properties =
        new IdentityProductAuthProperties(
            "cardo.session",
            "cardo.csrf",
            "identity",
            "reference-product",
            new IdentityProductAuthProperties.TokenExchange(
                Duration.ofSeconds(1), Duration.ofSeconds(1)),
            null);
    return new WebApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class))
        .withUserConfiguration(ReferenceIdentitySessionSecurity.class, TestRoutes.class)
        .withBean(IdentityProductAuthProperties.class, () -> properties)
        .withPropertyValues(
            "spring.security.oauth2.resourceserver.jwt.issuer-uri=" + issuer,
            "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=" + issuer + "/certs",
            "cardo.identity.product-auth.identity-session-audience=identity",
            "cardo.identity.product-auth.session-cookie-name=cardo.session",
            "cardo.identity.product-auth.csrf-cookie-name=cardo.csrf");
  }

  private int indexOf(SecurityFilterChain chain, Class<?> type) {
    for (int index = 0; index < chain.getFilters().size(); index++) {
      if (type.isInstance(chain.getFilters().get(index))) {
        return index;
      }
    }
    return -1;
  }

  private String token(boolean expiring) throws Exception {
    JWTClaimsSet.Builder claims =
        new JWTClaimsSet.Builder()
            .subject("identity-subject")
            .issuer(issuer)
            .audience(List.of("identity"))
            .claim(CardoJwtClaims.IDENTITY_USER_ID, UUID.randomUUID().toString())
            .issueTime(Date.from(Instant.now().minusSeconds(5)));
    if (expiring) {
      claims.expirationTime(Date.from(Instant.now().plusSeconds(300)));
    }
    SignedJWT token =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("reference-test-key").build(),
            claims.build());
    token.sign(new RSASSASigner(keys.getPrivate()));
    return token.serialize();
  }

  private void respond(com.sun.net.httpserver.HttpExchange exchange, String body)
      throws IOException {
    byte[] content = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(HttpStatus.OK.value(), content.length);
    exchange.getResponseBody().write(content);
    exchange.close();
  }

  @Configuration(proxyBeanMethods = false)
  static class TestRoutes {

    @Bean
    WorkflowController workflowController() {
      return new WorkflowController();
    }
  }

  @RestController
  static class WorkflowController {

    @PostMapping("/api/reference/invitations/{invitationId}/accept")
    String accept() {
      return "accepted";
    }
  }
}
