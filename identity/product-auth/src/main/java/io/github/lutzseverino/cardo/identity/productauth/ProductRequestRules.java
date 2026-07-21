package io.github.lutzseverino.cardo.identity.productauth;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

public final class ProductRequestRules {

  private final AuthorizeHttpRequestsConfigurer<HttpSecurity>
          .AuthorizationManagerRequestMatcherRegistry
      requests;

  ProductRequestRules(
      AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
          requests) {
    this.requests = requests;
  }

  public ProductRequestRules permitAll(String... paths) {
    requests.requestMatchers(paths).permitAll();
    return this;
  }

  public ProductRequestRules permitAll(HttpMethod method, String... paths) {
    requests.requestMatchers(method, paths).permitAll();
    return this;
  }

  public ProductRequestRules authenticated(String... paths) {
    requests.requestMatchers(paths).authenticated();
    return this;
  }

  public ProductRequestRules authenticated(HttpMethod method, String... paths) {
    requests.requestMatchers(method, paths).authenticated();
    return this;
  }

  public ProductRequestRules hasAuthority(String authority, HttpMethod method, String... paths) {
    requests.requestMatchers(method, paths).hasAuthority(authority);
    return this;
  }
}
