package io.github.lutzseverino.cardo.integration.reference;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ReferenceKeycloakActions {

  private static final Pattern FORM = Pattern.compile("(?is)<form\\b[^>]*>.*?</form>");
  private static final Pattern INPUT = Pattern.compile("(?is)<input\\b[^>]*>");
  private static final Pattern ERROR =
      Pattern.compile("(?is)<[^>]+id=[\"']kc-error-message[\"'][^>]*>(.*?)</[^>]+>");
  private static final Pattern ELEMENT = Pattern.compile("(?is)<[^>]+>");
  private final HttpClient browser =
      HttpClient.newBuilder()
          .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
          .connectTimeout(Duration.ofSeconds(2))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  Result complete(URI actionLink, String password, String firstName, String lastName) {
    Page page = get(actionLink);
    boolean passwordCompleted = false;
    boolean profileCompleted = false;
    for (int step = 0; step < 6; step++) {
      if (page.status() / 100 == 3) {
        URI redirect = page.uri().resolve(page.location());
        if (passwordCompleted && profileCompleted) {
          return new Result(true, true, redirect);
        }
        page = get(redirect);
        continue;
      }
      Form form;
      try {
        form = form(page.body(), page.uri());
      } catch (IllegalStateException missing) {
        throw new IllegalStateException(
            "Keycloak required-action form was missing: " + describe(page), missing);
      }
      Map<String, String> input = new LinkedHashMap<>(form.values());
      if ("kc-passwd-update-form".equals(form.id())) {
        input.put("password-new", password);
        input.put("password-confirm", password);
        passwordCompleted = true;
      } else if ("kc-update-profile-form".equals(form.id())) {
        input.put("firstName", firstName);
        input.put("lastName", lastName);
        profileCompleted = true;
      } else {
        throw new IllegalStateException("Unexpected Keycloak required-action form.");
      }
      page = post(form.action(), input);
    }
    throw new IllegalStateException("Keycloak required actions did not complete.");
  }

  static Form form(String html, URI page) {
    Matcher match = FORM.matcher(html);
    if (!match.find()) {
      throw new IllegalStateException("Keycloak required-action form was missing.");
    }
    String form = match.group();
    String tag = form.substring(0, form.indexOf('>') + 1);
    String id = attribute(tag, "id");
    String action = htmlDecode(attribute(tag, "action"));
    Map<String, String> values = new LinkedHashMap<>();
    Matcher inputs = INPUT.matcher(form);
    while (inputs.find()) {
      String input = inputs.group();
      String name = optionalAttribute(input, "name");
      if (name != null && !input.toLowerCase(Locale.ROOT).contains(" disabled")) {
        values.put(name, htmlDecode(optionalAttribute(input, "value", "")));
      }
    }
    return new Form(id, page.resolve(action), Map.copyOf(values));
  }

  private static String describe(Page page) {
    Matcher error = ERROR.matcher(page.body());
    String detail = error.find() ? ELEMENT.matcher(error.group(1)).replaceAll(" ") : "";
    detail = htmlDecode(detail).replaceAll("\\s+", " ").trim();
    return "status="
        + page.status()
        + ", path="
        + page.uri().getPath()
        + (detail.isEmpty() ? "" : ", error=" + detail);
  }

  private Page get(URI uri) {
    return send(HttpRequest.newBuilder(uri).GET().timeout(Duration.ofSeconds(5)).build());
  }

  private Page post(URI uri, Map<String, String> input) {
    String body =
        input.entrySet().stream()
            .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
            .collect(java.util.stream.Collectors.joining("&"));
    return send(
        HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build());
  }

  private Page send(HttpRequest request) {
    try {
      HttpResponse<String> response = browser.send(request, HttpResponse.BodyHandlers.ofString());
      return new Page(
          response.uri(),
          response.statusCode(),
          response.body(),
          response.headers().firstValue("Location").orElse(null));
    } catch (IOException failure) {
      throw new IllegalStateException("Keycloak required-action request failed.", failure);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Keycloak required-action request was interrupted.", interrupted);
    }
  }

  private static String attribute(String element, String name) {
    String value = optionalAttribute(element, name);
    if (value == null) {
      throw new IllegalStateException("Keycloak form attribute was missing.");
    }
    return value;
  }

  private static String optionalAttribute(String element, String name) {
    return optionalAttribute(element, name, null);
  }

  private static String optionalAttribute(String element, String name, String fallback) {
    Matcher match =
        Pattern.compile("(?is)\\b" + Pattern.quote(name) + "\\s*=\\s*[\"']([^\"']*)[\"']")
            .matcher(element);
    return match.find() ? match.group(1) : fallback;
  }

  private static String htmlDecode(String value) {
    return value
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">");
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  record Result(boolean passwordCompleted, boolean profileCompleted, URI redirect) {}

  record Form(String id, URI action, Map<String, String> values) {}

  private record Page(URI uri, int status, String body, String location) {}
}
