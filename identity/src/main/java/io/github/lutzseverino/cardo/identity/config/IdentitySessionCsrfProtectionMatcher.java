package io.github.lutzseverino.cardo.identity.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.RequestMatcher;

final class IdentitySessionCsrfProtectionMatcher implements RequestMatcher {

  private final Set<SessionMutation> protectedMutations;

  IdentitySessionCsrfProtectionMatcher(String basePath) {
    String sessionsPath = basePath + "/identity/sessions";
    String currentSessionPath = sessionsPath + "/current";
    this.protectedMutations =
        Set.of(
            new SessionMutation(HttpMethod.POST.name(), sessionsPath),
            new SessionMutation(HttpMethod.POST.name(), currentSessionPath + "/refresh"),
            new SessionMutation(HttpMethod.DELETE.name(), currentSessionPath));
  }

  @Override
  public boolean matches(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return protectedMutations.contains(new SessionMutation(request.getMethod(), path));
  }

  private record SessionMutation(String method, String path) {}
}
