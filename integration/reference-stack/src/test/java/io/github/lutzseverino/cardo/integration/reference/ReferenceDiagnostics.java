package io.github.lutzseverino.cardo.integration.reference;

import java.util.regex.Pattern;

final class ReferenceDiagnostics {

  private static final Pattern JWT =
      Pattern.compile("(?i)\\beyJ[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\b");
  private static final Pattern SECRET_FIELD =
      Pattern.compile(
          "(?i)(client[_-]?secret|password|authorization|cookie|set-cookie|token)([=:]\\s*)([^\\s,;]+)");
  private static final Pattern ACTION_LINK =
      Pattern.compile(
          "(?i)https?://[^\\s\"'<>]*(?:/login-actions/action-token\\?[^\\s\"'<>]*|/invitations/accept/[^\\s\"'<>]+|[?&](?:code|token)=[^\\s\"'<>]+)");

  private ReferenceDiagnostics() {}

  static String sanitize(String input) {
    if (input == null) {
      return "";
    }
    String sanitized = JWT.matcher(input).replaceAll("[redacted-jwt]");
    sanitized = ACTION_LINK.matcher(sanitized).replaceAll("[redacted-action-link]");
    return SECRET_FIELD.matcher(sanitized).replaceAll("$1$2[redacted]");
  }
}
