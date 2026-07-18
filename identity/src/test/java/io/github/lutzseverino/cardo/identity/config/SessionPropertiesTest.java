package io.github.lutzseverino.cardo.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.lutzseverino.cardo.identity.config.SessionProperties.Mode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class SessionPropertiesTest {

  @Test
  void acceptsExplicitLocalHttpPolicy() {
    new SessionProperties(
        Mode.LOCAL,
        "cardo.session",
        "cardo.refresh",
        "cardo.csrf",
        "/api/v1/identity/sessions/current",
        false);
  }

  @Test
  void acceptsExactProductionPolicy() {
    new SessionProperties(
        Mode.PRODUCTION,
        "__Host-cardo.session",
        "__Secure-cardo.refresh",
        "__Host-cardo.csrf",
        "/gateway/api/v1/identity/sessions/current",
        true);
  }

  @Test
  void rejectsInsecureOrIncorrectlyNamedProductionPolicy() {
    assertThatThrownBy(
            () ->
                new SessionProperties(
                    Mode.PRODUCTION,
                    "__Host-cardo.session",
                    "__Secure-cardo.refresh",
                    "__Host-cardo.csrf",
                    "/api/v1/identity/sessions/current",
                    false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be secure");

    assertThatThrownBy(
            () ->
                new SessionProperties(
                    Mode.PRODUCTION,
                    "cardo.session",
                    "__Secure-cardo.refresh",
                    "__Host-cardo.csrf",
                    "/api/v1/identity/sessions/current",
                    true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("__Host-cardo.session");

    assertThatThrownBy(
            () ->
                new SessionProperties(
                    Mode.PRODUCTION,
                    "__Host-cardo.session",
                    "__Secure-cardo.refresh",
                    "cardo.csrf",
                    "/api/v1/identity/sessions/current",
                    true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("__Host-cardo.csrf");
  }

  @Test
  void rejectsRefreshPathThatCanReachProductRoutes() {
    assertThatThrownBy(
            () ->
                new SessionProperties(
                    Mode.LOCAL, "cardo.session", "cardo.refresh", "cardo.csrf", "/", false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("/identity/sessions/current");
  }

  @Test
  void rejectsCookieNamesThatWouldShadowEachOther() {
    assertThatThrownBy(
            () ->
                new SessionProperties(
                    Mode.LOCAL,
                    "cardo.session",
                    "cardo.refresh",
                    "cardo.session",
                    "/api/v1/identity/sessions/current",
                    false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("distinct");
  }

  @Test
  void invalidProductionPolicyFailsApplicationStartup() {
    new ApplicationContextRunner()
        .withUserConfiguration(SessionConfiguration.class)
        .withPropertyValues(
            "cardo.identity.session.mode=production",
            "cardo.identity.session.access-cookie-name=cardo.session",
            "cardo.identity.session.refresh-cookie-name=cardo.refresh",
            "cardo.identity.session.csrf-cookie-name=cardo.csrf",
            "cardo.identity.session.refresh-cookie-path=/api/v1/identity/sessions/current",
            "cardo.identity.session.secure=false")
        .run(context -> assertThat(context.getStartupFailure()).isNotNull());
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(SessionProperties.class)
  static class SessionConfiguration {}
}
