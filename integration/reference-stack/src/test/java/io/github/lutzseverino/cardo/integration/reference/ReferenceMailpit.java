package io.github.lutzseverino.cardo.integration.reference;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ReferenceMailpit {

  private static final Pattern LINK = Pattern.compile("https?://[^\\s<>\\\"']+");
  private final ReferenceHttp http = ReferenceHttp.plain();
  private final String baseUrl;

  ReferenceMailpit(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  URI awaitLink(String recipient, String subject, Duration timeout) {
    return awaitLink(recipient, subject::equals, timeout);
  }

  URI awaitLink(String recipient, Predicate<String> subject, Duration timeout) {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      URI link = findLink(recipient, subject);
      if (link != null) {
        return link;
      }
      try {
        Thread.sleep(100);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while awaiting reference mail.", interrupted);
      }
    }
    throw new IllegalStateException("Expected reference mail was not delivered.");
  }

  @SuppressWarnings("unchecked")
  private URI findLink(String recipient, Predicate<String> subject) {
    ReferenceHttp.Response response =
        http.request("GET", URI.create(baseUrl + "/api/v1/messages"), null, Map.of());
    if (response.status() != 200) {
      return null;
    }
    Object value = http.object(response).get("messages");
    if (!(value instanceof List<?> messages)) {
      return null;
    }
    for (Object candidate : messages) {
      Map<String, Object> message = (Map<String, Object>) candidate;
      if (!subject.test(String.valueOf(message.get("Subject"))) || !sentTo(message, recipient)) {
        continue;
      }
      ReferenceHttp.Response body =
          http.request(
              "GET", URI.create(baseUrl + "/api/v1/message/" + message.get("ID")), null, Map.of());
      if (body.status() == 200) {
        Map<String, Object> content = http.object(body);
        return firstLink(String.valueOf(content.getOrDefault("Text", "")))
            .orElseGet(
                () -> firstLink(String.valueOf(content.getOrDefault("HTML", ""))).orElse(null));
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private boolean sentTo(Map<String, Object> message, String recipient) {
    Object value = message.get("To");
    if (!(value instanceof List<?> recipients)) {
      return false;
    }
    return recipients.stream()
        .map(item -> (Map<String, Object>) item)
        .map(item -> String.valueOf(item.get("Address")))
        .anyMatch(recipient::equalsIgnoreCase);
  }

  static Optional<URI> firstLink(String content) {
    Matcher matcher = LINK.matcher(content);
    if (!matcher.find()) {
      return Optional.empty();
    }
    String link = matcher.group().replace("&amp;", "&").replaceAll("[).,;]+$", "");
    return Optional.of(URI.create(link));
  }
}
