package io.github.lutzseverino.cardo.integration.reference;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class ReferenceProcess implements AutoCloseable {

  private static final int LOG_TAIL_LINES = 200;
  private final String name;
  private final Process process;
  private final Deque<String> logTail = new ArrayDeque<>();
  private final Thread outputReader;

  private ReferenceProcess(String name, Process process) {
    this.name = name;
    this.process = process;
    outputReader =
        Thread.ofPlatform().daemon().name("reference-" + name + "-output").start(this::readOutput);
  }

  static ReferenceProcess start(String name, Path jar, Map<String, String> environment) {
    if (!Files.isRegularFile(jar)) {
      throw new IllegalStateException("Checkout executable JAR is missing for " + name + ".");
    }
    ProcessBuilder builder =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-jar",
                jar.toString())
            .redirectErrorStream(true);
    configureEnvironment(builder.environment(), environment);
    try {
      return new ReferenceProcess(name, builder.start());
    } catch (IOException failure) {
      throw new IllegalStateException("Could not start checkout JAR for " + name + ".", failure);
    }
  }

  static void configureEnvironment(
      Map<String, String> processEnvironment, Map<String, String> serviceEnvironment) {
    processEnvironment.putAll(serviceEnvironment);
    processEnvironment.remove("KC_BOOTSTRAP_ADMIN_USERNAME");
    processEnvironment.remove("KC_BOOTSTRAP_ADMIN_PASSWORD");
  }

  void awaitReady(URI readiness, Duration timeout) {
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    Instant deadline = Instant.now().plus(timeout);
    RuntimeException last = null;
    while (Instant.now().isBefore(deadline)) {
      if (!process.isAlive()) {
        throw new IllegalStateException(name + " exited before readiness.\n" + sanitizedTail());
      }
      try {
        HttpResponse<Void> response =
            client.send(
                HttpRequest.newBuilder(readiness).timeout(Duration.ofSeconds(2)).GET().build(),
                HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() == 200) {
          return;
        }
      } catch (IOException failure) {
        last = new IllegalStateException("Readiness request failed.", failure);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while awaiting " + name + ".", interrupted);
      }
      try {
        Thread.sleep(100);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while awaiting " + name + ".", interrupted);
      }
    }
    throw new IllegalStateException(
        name
            + " did not become ready before its deadline."
            + (last == null ? "" : " " + last.getMessage())
            + "\n"
            + sanitizedTail());
  }

  void writeDiagnostics(Path directory) {
    try {
      Files.createDirectories(directory);
      Files.writeString(directory.resolve(name + ".log"), sanitizedTail(), StandardCharsets.UTF_8);
    } catch (IOException failure) {
      throw new IllegalStateException(
          "Could not write sanitized " + name + " diagnostics.", failure);
    }
  }

  private void readOutput() {
    try (BufferedReader lines =
        new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = lines.readLine()) != null) {
        synchronized (logTail) {
          if (logTail.size() == LOG_TAIL_LINES) {
            logTail.removeFirst();
          }
          logTail.addLast(line);
        }
      }
    } catch (IOException ignored) {
      // Process teardown can close the stream while the daemon reader is blocked.
    }
  }

  private String sanitizedTail() {
    synchronized (logTail) {
      return ReferenceDiagnostics.sanitize(String.join(System.lineSeparator(), logTail));
    }
  }

  @Override
  public void close() {
    process.destroy();
    try {
      if (!process.waitFor(25, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        process.waitFor(5, TimeUnit.SECONDS);
      }
      outputReader.join(Duration.ofSeconds(2));
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
    }
  }
}
