package io.github.lutzseverino.cardo.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class BillingMessageBundleTest {
  private static final Pattern API_EXCEPTION_CODE =
      Pattern.compile(
          "ApiException\\.(?:badRequest|conflict|forbidden|notFound|of)\\(\\s*(?:\\d+\\s*,\\s*)?\"([a-z0-9_]+)\"");
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_]+)}");

  @Test
  void spanishBundleHasDefaultBundleKeyAndPlaceholderParity() throws IOException {
    Properties defaults = messages("messages.properties");
    Properties spanish = messages("messages_es.properties");

    assertThat(keys(spanish)).containsExactlyElementsOf(keys(defaults));
    for (String key : keys(defaults)) {
      assertThat(placeholders(spanish.getProperty(key)))
          .as(key)
          .containsExactlyElementsOf(placeholders(defaults.getProperty(key)));
    }
  }

  @Test
  void defaultBundleCoversBackendAuthoredErrorCodes() throws IOException {
    Properties defaults = messages("messages.properties");
    Set<String> expectedKeys =
        errorCodes().stream().map(code -> "api_error." + code).collect(Collectors.toSet());

    assertThat(keys(defaults)).containsAll(expectedKeys);
  }

  private Properties messages(String resourceName) throws IOException {
    Properties messages = new Properties();
    try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourceName)) {
      assertThat(stream).as(resourceName).isNotNull();
      messages.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
    return messages;
  }

  private Set<String> keys(Properties messages) {
    return new TreeSet<>(messages.stringPropertyNames());
  }

  private Set<String> errorCodes() throws IOException {
    try (var files =
        Files.find(
            Path.of("src/main/java"), Integer.MAX_VALUE, (path, attrs) -> attrs.isRegularFile())) {
      return files
          .filter(path -> path.toString().endsWith(".java"))
          .flatMap(
              path -> {
                try {
                  return matches(API_EXCEPTION_CODE, Files.readString(path));
                } catch (IOException exception) {
                  throw new java.io.UncheckedIOException(exception);
                }
              })
          .collect(Collectors.toCollection(TreeSet::new));
    }
  }

  private java.util.stream.Stream<String> matches(Pattern pattern, String content) {
    return pattern.matcher(content).results().map(result -> result.group(1));
  }

  private Set<String> placeholders(String value) {
    return PLACEHOLDER
        .matcher(value)
        .results()
        .map(MatchResult::group)
        .collect(Collectors.toCollection(TreeSet::new));
  }
}
