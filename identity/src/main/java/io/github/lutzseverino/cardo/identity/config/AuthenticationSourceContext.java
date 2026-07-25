package io.github.lutzseverino.cardo.identity.config;

import io.github.lutzseverino.cardo.identity.model.AuthenticationSource;
import jakarta.servlet.http.HttpServletRequest;

/** Carries the credential source selected by the bearer-token resolver into the controller. */
public final class AuthenticationSourceContext {

  private static final String ATTRIBUTE = AuthenticationSourceContext.class.getName() + ".source";

  private AuthenticationSourceContext() {}

  public static void set(HttpServletRequest request, AuthenticationSource source) {
    request.setAttribute(ATTRIBUTE, source);
  }

  public static AuthenticationSource require(HttpServletRequest request) {
    Object source = request.getAttribute(ATTRIBUTE);
    if (source instanceof AuthenticationSource authenticationSource) {
      return authenticationSource;
    }
    throw new IllegalStateException("Authenticated request did not declare its credential source.");
  }
}
