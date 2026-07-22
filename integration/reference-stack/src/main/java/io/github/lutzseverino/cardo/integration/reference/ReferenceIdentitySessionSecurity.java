package io.github.lutzseverino.cardo.integration.reference;

import io.github.lutzseverino.cardo.authorization.spring.CardoJwtClaims;
import io.github.lutzseverino.cardo.authorization.spring.ExactAudienceValidator;
import io.github.lutzseverino.cardo.authorization.spring.RequiredExpirationValidator;
import io.github.lutzseverino.cardo.identity.productauth.IdentityProductAuthProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

@Configuration(proxyBeanMethods = false)
class ReferenceIdentitySessionSecurity {

  private static final OAuth2Error INVALID_USER =
      new OAuth2Error("invalid_token", "The Cardo identity user claim is invalid.", null);

  @Bean("referenceIdentitySessionJwtDecoder")
  JwtDecoder referenceIdentitySessionJwtDecoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
      @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") String jwkSetUri,
      @Value("${cardo.identity.product-auth.identity-session-audience}") String audience,
      IdentityProductAuthProperties properties) {
    RestOperations jwkHttp = jwkRestOperations(properties.tokenExchange());
    NimbusJwtDecoder decoder =
        jwkSetUri == null || jwkSetUri.isBlank()
            ? NimbusJwtDecoder.withIssuerLocation(issuer).restOperations(jwkHttp).build()
            : NimbusJwtDecoder.withJwkSetUri(jwkSetUri).restOperations(jwkHttp).build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer),
            new RequiredExpirationValidator(),
            new ExactAudienceValidator(audience),
            ReferenceIdentitySessionSecurity::validIdentityUser));
    return decoder;
  }

  @Bean
  @Order(-1)
  SecurityFilterChain referenceIdentityWorkflowSecurity(
      HttpSecurity http,
      @Qualifier("referenceIdentitySessionJwtDecoder") JwtDecoder identitySessions,
      @Value("${cardo.identity.product-auth.session-cookie-name}") String sessionCookie,
      @Value("${cardo.identity.product-auth.csrf-cookie-name}") String csrfCookie)
      throws Exception {
    JwtAuthenticationConverter authentication = new JwtAuthenticationConverter();
    authentication.setJwtGrantedAuthoritiesConverter(ignored -> List.of());
    JwtAuthenticationProvider provider = new JwtAuthenticationProvider(identitySessions);
    provider.setJwtAuthenticationConverter(authentication);
    AuthenticationManager identityTokens = new ProviderManager(provider);
    BearerTokenAuthenticationEntryPoint authenticationEntryPoint =
        new BearerTokenAuthenticationEntryPoint();
    ReferenceIdentitySessionAuthenticationFilter identitySessionAuthentication =
        new ReferenceIdentitySessionAuthenticationFilter(
            identityTokens,
            new ReferenceIdentitySessionCookieResolver(sessionCookie),
            authenticationEntryPoint);
    http.securityMatcher(workflowRequests())
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(new ReferenceReadOnlyCsrfTokenRepository(csrfCookie))
                    .csrfTokenRequestHandler(new ReferenceHeaderOnlyCsrfTokenRequestHandler())
                    .requireCsrfProtectionMatcher(acceptanceRequests()))
        .exceptionHandling(
            exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint))
        .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
        .addFilterBefore(identitySessionAuthentication, CsrfFilter.class);
    return http.build();
  }

  private RestOperations jwkRestOperations(IdentityProductAuthProperties.TokenExchange timeouts) {
    return new RestTemplate(requestFactory(timeouts.connectTimeout(), timeouts.readTimeout()));
  }

  private SimpleClientHttpRequestFactory requestFactory(Duration connect, Duration read) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connect);
    factory.setReadTimeout(read);
    return factory;
  }

  static RequestMatcher workflowRequests() {
    return new OrRequestMatcher(
        acceptanceRequests(),
        PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/api/reference/convergence/*"));
  }

  static RequestMatcher acceptanceRequests() {
    return PathPatternRequestMatcher.pathPattern(
        HttpMethod.POST, "/api/reference/invitations/*/accept");
  }

  static OAuth2TokenValidatorResult validIdentityUser(Jwt token) {
    String value = token.getClaimAsString(CardoJwtClaims.IDENTITY_USER_ID);
    try {
      return value != null && !value.isBlank() && UUID.fromString(value) != null
          ? OAuth2TokenValidatorResult.success()
          : OAuth2TokenValidatorResult.failure(INVALID_USER);
    } catch (IllegalArgumentException failure) {
      return OAuth2TokenValidatorResult.failure(INVALID_USER);
    }
  }
}

final class ReferenceIdentitySessionCookieResolver implements BearerTokenResolver {

  private static final OAuth2Error EXPLICIT_AUTHORIZATION_REJECTED =
      new OAuth2Error(
          "invalid_request",
          "Authorization headers are not accepted on the Identity session workflow.",
          null);

  private final String cookieName;

  ReferenceIdentitySessionCookieResolver(String cookieName) {
    this.cookieName = cookieName;
  }

  @Override
  public String resolve(HttpServletRequest request) {
    if (request.getHeader(HttpHeaders.AUTHORIZATION) != null) {
      throw new OAuth2AuthenticationException(EXPLICIT_AUTHORIZATION_REJECTED);
    }
    if (request.getCookies() == null) {
      return null;
    }
    for (Cookie cookie : request.getCookies()) {
      if (cookieName.equals(cookie.getName())
          && cookie.getValue() != null
          && !cookie.getValue().isBlank()) {
        return cookie.getValue();
      }
    }
    return null;
  }
}

final class ReferenceIdentitySessionAuthenticationFilter extends BearerTokenAuthenticationFilter {

  ReferenceIdentitySessionAuthenticationFilter(
      AuthenticationManager authenticationManager,
      BearerTokenResolver bearerTokenResolver,
      BearerTokenAuthenticationEntryPoint authenticationEntryPoint) {
    super(authenticationManager, authenticationConverter(bearerTokenResolver));
    setAuthenticationEntryPoint(authenticationEntryPoint);
  }

  private static BearerTokenAuthenticationConverter authenticationConverter(
      BearerTokenResolver bearerTokenResolver) {
    BearerTokenAuthenticationConverter converter = new BearerTokenAuthenticationConverter();
    converter.setBearerTokenResolver(bearerTokenResolver);
    return converter;
  }
}

final class ReferenceReadOnlyCsrfTokenRepository implements CsrfTokenRepository {

  private final CookieCsrfTokenRepository delegate;

  ReferenceReadOnlyCsrfTokenRepository(String cookieName) {
    delegate = CookieCsrfTokenRepository.withHttpOnlyFalse();
    delegate.setCookieName(cookieName);
    delegate.setHeaderName("X-CSRF-TOKEN");
    delegate.setCookiePath("/");
  }

  @Override
  public CsrfToken generateToken(HttpServletRequest request) {
    return delegate.generateToken(request);
  }

  @Override
  public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
    // Identity exclusively owns CSRF cookie creation and expiry.
  }

  @Override
  public CsrfToken loadToken(HttpServletRequest request) {
    return delegate.loadToken(request);
  }
}

final class ReferenceHeaderOnlyCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {

  @Override
  public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
    return request.getHeader(csrfToken.getHeaderName());
  }
}
