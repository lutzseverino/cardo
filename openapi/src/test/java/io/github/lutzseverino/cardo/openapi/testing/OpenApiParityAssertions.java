package io.github.lutzseverino.cardo.openapi.testing;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Assertions shared by service tests that compare the served and canonical OpenAPI documents. */
public final class OpenApiParityAssertions {

  private static final Set<String> HTTP_METHODS =
      Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace");

  private OpenApiParityAssertions() {}

  public static void assertMatches(
      String canonicalSpecification, String runtimeContents, String runtimePrefix) {
    JsonNode canonical =
        resolved(
            new OpenAPIV3Parser()
                .readLocation(
                    Path.of(canonicalSpecification).toAbsolutePath().toString(),
                    null,
                    resolveFully()));
    JsonNode runtime =
        resolved(new OpenAPIV3Parser().readContents(runtimeContents, null, resolveFully()));

    assertThat(runtime.path("openapi").asText()).startsWith("3.0.");
    assertThat(normalizedInfo(runtime)).isEqualTo(normalizedInfo(canonical));
    assertThat(serverUrls(runtime)).isEqualTo(serverUrls(canonical));
    assertThat(normalizedSecuritySchemes(runtime)).isEqualTo(normalizedSecuritySchemes(canonical));
    assertThat(normalizedOperations(runtime, runtimePrefix))
        .isEqualTo(normalizedOperations(canonical, ""));
  }

  private static ParseOptions resolveFully() {
    ParseOptions options = new ParseOptions();
    options.setResolve(true);
    options.setResolveFully(true);
    return options;
  }

  private static JsonNode resolved(SwaggerParseResult result) {
    assertThat(result.getMessages()).isEmpty();
    return Json.mapper().valueToTree(result.getOpenAPI());
  }

  private static List<String> normalizedInfo(JsonNode document) {
    JsonNode info = document.path("info");
    return List.of(
        info.path("title").asText(),
        info.path("version").asText(),
        normalizeWhitespace(info.path("description").asText()),
        info.path("license").path("name").asText(),
        info.path("license").path("url").asText());
  }

  private static List<String> normalizedSecuritySchemes(JsonNode document) {
    List<String> schemes = new ArrayList<>();
    document
        .path("components")
        .path("securitySchemes")
        .fields()
        .forEachRemaining(
            entry -> {
              JsonNode scheme = entry.getValue();
              schemes.add(
                  String.join(
                      "|",
                      entry.getKey(),
                      scheme.path("type").asText(),
                      scheme.path("scheme").asText(),
                      scheme.path("bearerFormat").asText(),
                      scheme.path("in").asText(),
                      scheme.path("name").asText()));
            });
    return schemes.stream().sorted().toList();
  }

  private static List<String> serverUrls(JsonNode document) {
    List<String> urls = new ArrayList<>();
    document.path("servers").forEach(server -> urls.add(server.path("url").asText()));
    return urls.stream().sorted().toList();
  }

  private static List<String> normalizedOperations(JsonNode document, String pathPrefix) {
    List<String> operations = new ArrayList<>();
    document
        .path("paths")
        .fields()
        .forEachRemaining(
            path -> {
              String normalizedPath = removePrefix(path.getKey(), pathPrefix);
              path.getValue()
                  .fields()
                  .forEachRemaining(
                      method -> {
                        if (HTTP_METHODS.contains(method.getKey())) {
                          JsonNode operation = method.getValue();
                          operations.add(
                              String.join(
                                  "|",
                                  normalizedPath,
                                  method.getKey(),
                                  operation.path("operationId").asText(),
                                  parameters(path.getValue(), operation),
                                  requestBody(operation),
                                  responses(operation),
                                  security(operation)));
                        }
                      });
            });
    return operations.stream().sorted().toList();
  }

  private static String parameters(JsonNode path, JsonNode operation) {
    TreeSet<String> parameters = new TreeSet<>();
    addParameters(parameters, path.path("parameters"));
    addParameters(parameters, operation.path("parameters"));
    return String.join(",", parameters);
  }

  private static void addParameters(TreeSet<String> normalized, JsonNode parameters) {
    parameters.forEach(
        parameter ->
            normalized.add(
                String.join(
                    ":",
                    parameter.path("name").asText(),
                    parameter.path("in").asText(),
                    Boolean.toString(parameter.path("required").asBoolean()),
                    parameter.has("schema")
                        ? schema(parameter.path("schema"))
                        : responseContent(parameter.path("content")))));
  }

  private static String requestBody(JsonNode operation) {
    JsonNode body = operation.path("requestBody");
    if (body.isMissingNode()) {
      return "";
    }
    return body.path("required").asBoolean() + ":" + responseContent(body.path("content"));
  }

  private static String responses(JsonNode operation) {
    TreeSet<String> responses = new TreeSet<>();
    operation
        .path("responses")
        .fields()
        .forEachRemaining(
            response ->
                responses.add(
                    response.getKey()
                        + ":"
                        + responseContent(response.getValue().path("content"))));
    return String.join(",", responses);
  }

  private static String responseContent(JsonNode content) {
    TreeSet<String> media = new TreeSet<>();
    content
        .fields()
        .forEachRemaining(
            entry -> media.add(entry.getKey() + "=" + schema(entry.getValue().path("schema"))));
    return String.join("+", media);
  }

  private static String schema(JsonNode schema) {
    if (schema.has("$ref")) {
      String reference = schema.path("$ref").asText();
      return reference.substring(reference.lastIndexOf('/') + 1);
    }
    List<String> parts = new ArrayList<>();
    parts.add(schema.path("type").asText());
    parts.add(schema.path("format").asText());
    if (schema.path("nullable").asBoolean()) {
      parts.add("nullable");
    }
    if (schema.has("enum")) {
      TreeSet<String> values = new TreeSet<>();
      schema.path("enum").forEach(value -> values.add(value.asText()));
      parts.add("enum=" + values);
    }
    if (schema.has("required")) {
      TreeSet<String> required = new TreeSet<>();
      schema.path("required").forEach(value -> required.add(value.asText()));
      parts.add("required=" + required);
    }
    if (schema.has("items")) {
      parts.add("items=" + schema(schema.path("items")));
    }
    if (schema.has("properties")) {
      TreeSet<String> properties = new TreeSet<>();
      schema
          .path("properties")
          .fields()
          .forEachRemaining(
              property -> properties.add(property.getKey() + "=" + schema(property.getValue())));
      parts.add("properties=" + properties);
    }
    return String.join("/", parts);
  }

  private static String security(JsonNode operation) {
    if (!operation.has("security") || operation.path("security").isEmpty()) {
      return "";
    }
    List<String> alternatives = new ArrayList<>();
    for (JsonNode alternative : operation.path("security")) {
      TreeSet<String> names = new TreeSet<>();
      Iterator<Map.Entry<String, JsonNode>> fields = alternative.fields();
      fields.forEachRemaining(entry -> names.add(entry.getKey()));
      alternatives.add(String.join("+", names));
    }
    return alternatives.stream().sorted().toList().toString();
  }

  private static String removePrefix(String path, String prefix) {
    return !prefix.isEmpty() && path.startsWith(prefix) ? path.substring(prefix.length()) : path;
  }

  private static String normalizeWhitespace(String value) {
    return value.trim().replaceAll("\\s+", " ");
  }
}
