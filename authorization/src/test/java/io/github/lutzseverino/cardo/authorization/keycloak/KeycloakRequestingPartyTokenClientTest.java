package io.github.lutzseverino.cardo.authorization.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.lutzseverino.cardo.authorization.token.RequestingPartyToken;
import io.github.lutzseverino.cardo.authorization.token.RequestingPartyTokenRequest;
import java.net.URI;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KeycloakRequestingPartyTokenClientTest {

  @Test
  void exchangesIdentityCredentialForRequestedProductAudience() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakRequestingPartyTokenClient client =
        new KeycloakRequestingPartyTokenClient(
            URI.create("https://identity.test/realms/cardo/protocol/openid-connect/token"), rest);
    server
        .expect(requestTo("https://identity.test/realms/cardo/protocol/openid-connect/token"))
        .andExpect(method(POST))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer identity-token"))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
        .andExpect(content().string(Matchers.containsString("audience=polity")))
        .andExpect(content().string(Matchers.not(Matchers.containsString("permission="))))
        .andRespond(
            withSuccess("{\"access_token\":\"product-token\"}", MediaType.APPLICATION_JSON));

    RequestingPartyToken token =
        client.authorize(RequestingPartyTokenRequest.allPermissions("identity-token", "polity"));

    assertThat(token.token()).isEqualTo("product-token");
    server.verify();
  }
}
