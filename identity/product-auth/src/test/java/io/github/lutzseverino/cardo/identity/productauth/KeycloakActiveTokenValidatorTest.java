package io.github.lutzseverino.cardo.identity.productauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class KeycloakActiveTokenValidatorTest {

  @Test
  void returnsActiveIntrospectionResultAndCachesPositiveResponses() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakActiveTokenValidator validator = validator(rest, Duration.ofSeconds(10));
    server
        .expect(requestTo("https://keycloak.example/introspect"))
        .andExpect(method(POST))
        .andExpect(content().string(Matchers.containsString("client_id=clinic")))
        .andExpect(content().string(Matchers.containsString("token=access-token")))
        .andRespond(withSuccess("{\"active\":true}", MediaType.APPLICATION_JSON));

    assertThat(validator.isActive("access-token")).isTrue();
    assertThat(validator.isActive("access-token")).isTrue();

    server.verify();
  }

  @Test
  void returnsFalseForInactiveTokens() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakActiveTokenValidator validator = validator(rest, Duration.ofSeconds(10));
    server
        .expect(requestTo("https://keycloak.example/introspect"))
        .andExpect(method(POST))
        .andRespond(withSuccess("{\"active\":false}", MediaType.APPLICATION_JSON));

    assertThat(validator.isActive("access-token")).isFalse();

    server.verify();
  }

  @Test
  void propagatesProviderFailuresForFailClosedFilterHandling() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakActiveTokenValidator validator = validator(rest, Duration.ofSeconds(10));
    server
        .expect(requestTo("https://keycloak.example/introspect"))
        .andExpect(method(POST))
        .andRespond(withServerError());

    assertThatThrownBy(() -> validator.isActive("access-token"))
        .isInstanceOf(RestClientException.class);

    server.verify();
  }

  private KeycloakActiveTokenValidator validator(RestClient.Builder rest, Duration cacheTtl) {
    return new KeycloakActiveTokenValidator(
        URI.create("https://keycloak.example/introspect"),
        "clinic",
        "clinic-secret",
        cacheTtl,
        2048,
        rest.build(),
        Clock.fixed(Instant.parse("2026-06-23T12:00:00Z"), ZoneOffset.UTC));
  }
}
