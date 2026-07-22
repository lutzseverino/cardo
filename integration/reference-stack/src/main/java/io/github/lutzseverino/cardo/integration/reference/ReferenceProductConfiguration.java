package io.github.lutzseverino.cardo.integration.reference;

import io.github.lutzseverino.cardo.authorization.AuthorizationAdminClient;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakAuthorizationClient;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakClientCredentialsTokenSettings;
import io.github.lutzseverino.cardo.identity.productauth.ProductRequestPolicy;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class ReferenceProductConfiguration {

  private static final Duration OUTBOUND_CONNECT_TIMEOUT = Duration.ofSeconds(2);
  private static final Duration OUTBOUND_READ_TIMEOUT = Duration.ofSeconds(2);

  @Bean
  RestClient.Builder referenceRestClientBuilder() {
    return RestClient.builder()
        .requestFactory(requestFactory(OUTBOUND_CONNECT_TIMEOUT, OUTBOUND_READ_TIMEOUT));
  }

  @Bean
  @Primary
  KeycloakClientCredentialsTokenProvider referenceServiceTokens(
      @Value("${reference.keycloak.base-url}") String baseUrl,
      @Value("${reference.keycloak.realm}") String realm,
      @Value("${reference.keycloak.outbound-client-id}") String clientId,
      @Value("${reference.keycloak.outbound-client-secret}") String clientSecret,
      RestClient.Builder rest) {
    return tokenProvider(baseUrl, realm, clientId, clientSecret, rest);
  }

  @Bean
  KeycloakClientCredentialsTokenProvider referenceCatalogTokens(
      @Value("${reference.keycloak.base-url}") String baseUrl,
      @Value("${reference.keycloak.realm}") String realm,
      @Value("${reference.keycloak.catalog-client-id}") String clientId,
      @Value("${reference.keycloak.catalog-client-secret}") String clientSecret,
      RestClient.Builder rest) {
    return tokenProvider(baseUrl, realm, clientId, clientSecret, rest);
  }

  @Bean
  AuthorizationAdminClient referenceAuthorization(
      @Value("${reference.keycloak.base-url}") String baseUrl,
      @Value("${reference.keycloak.realm}") String realm,
      @Qualifier("referenceCatalogTokens") KeycloakClientCredentialsTokenProvider catalogTokens,
      ReferenceGrantGate gate,
      RestClient.Builder rest) {
    AuthorizationAdminClient productCatalog =
        new KeycloakAuthorizationClient(
            baseUrl,
            realm,
            ReferenceContract.PRODUCT_CLIENT,
            rest.clone(),
            catalogTokens::clientCredentialsToken,
            () -> {
              throw new IllegalStateException(
                  "The reference product has no realm-administration credential.");
            });
    return gate.guard(productCatalog);
  }

  @Bean
  ProductRequestPolicy referenceRequestPolicy() {
    return rules ->
        rules
            .permitAll(HttpMethod.GET, "/")
            .permitAll("/invitations/**")
            .permitAll("/internal/reference/**")
            .authenticated(HttpMethod.POST, "/api/reference/invitations")
            .hasAuthority(
                ReferenceContract.TENANT_AUTHORITY, HttpMethod.GET, "/api/reference/tenants/*")
            .authenticated("/api/reference/billing/**");
  }

  private KeycloakClientCredentialsTokenProvider tokenProvider(
      String baseUrl, String realm, String clientId, String clientSecret, RestClient.Builder rest) {
    return new KeycloakClientCredentialsTokenProvider(
        baseUrl,
        realm,
        clientId,
        clientSecret,
        rest.clone(),
        new KeycloakClientCredentialsTokenSettings(
            Duration.ofSeconds(2), Duration.ofSeconds(2), Duration.ofSeconds(5)));
  }

  private SimpleClientHttpRequestFactory requestFactory(Duration connect, Duration read) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connect);
    factory.setReadTimeout(read);
    return factory;
  }
}
