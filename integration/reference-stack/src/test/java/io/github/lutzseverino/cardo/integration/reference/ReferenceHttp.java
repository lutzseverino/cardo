package io.github.lutzseverino.cardo.integration.reference;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

final class ReferenceHttp {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private final HttpClient client;

  ReferenceHttp(HttpClient client) {
    this.client = client;
  }

  static ReferenceHttp plain() {
    return new ReferenceHttp(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
  }

  Response request(String method, URI uri, Object body, Map<String, String> headers) {
    try {
      HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5));
      headers.forEach(request::header);
      if (body == null) {
        request.method(method, HttpRequest.BodyPublishers.noBody());
      } else {
        request.header("Content-Type", "application/json");
        request.method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
      }
      HttpResponse<String> response =
          client.send(request.build(), HttpResponse.BodyHandlers.ofString());
      return new Response(response.statusCode(), response.body(), response.headers().map());
    } catch (IOException failure) {
      throw new IllegalStateException("Reference HTTP request failed.", failure);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Reference HTTP request was interrupted.", interrupted);
    }
  }

  Map<String, Object> object(Response response) {
    return JSON.readValue(response.body(), new TypeReference<>() {});
  }

  record Response(int status, String body, Map<String, java.util.List<String>> headers) {}
}
