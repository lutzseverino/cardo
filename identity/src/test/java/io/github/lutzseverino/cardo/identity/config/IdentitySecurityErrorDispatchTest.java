package io.github.lutzseverino.cardo.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    classes = IdentitySecurityErrorDispatchTest.Application.class,
    properties = {
      "cardo.api.base-path=/api/v1",
      "cardo.identity.session.mode=production",
      "cardo.identity.session.access-cookie-name=__Host-cardo.session",
      "cardo.identity.session.refresh-cookie-name=__Secure-cardo.refresh",
      "cardo.identity.session.csrf-cookie-name=__Host-cardo.csrf",
      "cardo.identity.session.refresh-cookie-path=/api/v1/identity/sessions/current",
      "cardo.identity.session.secure=true",
      "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://identity.example/realms/cardo"
    })
class IdentitySecurityErrorDispatchTest {

  @LocalServerPort private int port;
  @MockitoBean private JwtDecoder decoder;

  @Test
  void preservesCsrfFailuresAcrossContainerErrorDispatch() throws Exception {
    assertThat(postSession(null, null).statusCode()).isEqualTo(403);
    assertThat(postSession("csrf-token", "different-token").statusCode()).isEqualTo(403);

    assertThat(get("/api/v1/identity/users/me").statusCode()).isEqualTo(401);
    assertThat(get("/error").statusCode()).isEqualTo(401);
  }

  private HttpResponse<String> postSession(String cookieToken, String headerToken)
      throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(uri("/api/v1/identity/sessions"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{}"));
    if (cookieToken != null) {
      request.header("Cookie", "__Host-cardo.csrf=" + cookieToken);
    }
    if (headerToken != null) {
      request.header("X-CSRF-TOKEN", headerToken);
    }
    return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> get(String path) throws Exception {
    return HttpClient.newHttpClient()
        .send(
            HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
  }

  private URI uri(String path) {
    return URI.create("http://127.0.0.1:" + port + path);
  }

  @Configuration(proxyBeanMethods = false)
  @Import({SecurityConfig.class, TrafficController.class, Properties.class})
  @ImportAutoConfiguration({
    TomcatServletWebServerAutoConfiguration.class,
    DispatcherServletAutoConfiguration.class,
    WebMvcAutoConfiguration.class,
    ErrorMvcAutoConfiguration.class,
    SecurityAutoConfiguration.class,
    ServletWebSecurityAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class
  })
  static class Application {}

  @RestController
  static class TrafficController {

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
