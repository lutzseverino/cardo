package io.github.lutzseverino.cardo.integration.reference;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

final class ReferenceStackHarness implements AutoCloseable {

  static final String CONTROL_SECRET = "reference-control-secret";
  private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(2);

  private final Path checkout = Path.of(System.getProperty("cardo.reference.checkout"));
  private final String version = System.getProperty("cardo.reference.version");
  private final Path diagnostics =
      checkout.resolve("integration/reference-stack/target/reference-stack");
  private final Network network = Network.newNetwork();
  private final ReferenceDatabaseCluster databases = new ReferenceDatabaseCluster();
  private final GenericContainer<?> mailpit =
      new GenericContainer<>(DockerImageName.parse(System.getProperty("cardo.test.mailpit.image")))
          .withNetwork(network)
          .withNetworkAliases("mailpit")
          .withExposedPorts(1025, 8025)
          .waitingFor(Wait.forHttp("/api/v1/info").forStatusCode(200));
  private final GenericContainer<?> keycloak =
      new GenericContainer<>(DockerImageName.parse(System.getProperty("cardo.test.keycloak.image")))
          .withNetwork(network)
          .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", ReferenceKeycloakMaterializer.ADMIN_USERNAME)
          .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", ReferenceKeycloakMaterializer.ADMIN_PASSWORD)
          .withCommand("start-dev", "--http-enabled=true", "--hostname-strict=false")
          .withExposedPorts(8080)
          .waitingFor(
              Wait.forHttp("/realms/master")
                  .forStatusCode(200)
                  .withStartupTimeout(STARTUP_TIMEOUT));
  private final List<ReferenceProcess> processes = new ArrayList<>();
  private final List<String> milestones = new ArrayList<>();

  private final int identityPort = freePort();
  private final int invitePort = freePort();
  private final int billingPort = freePort();
  private final int productPort = freePort();

  private ReferenceHttpsOrigin origin;
  private ReferenceKeycloakMaterializer keycloakContract;

  void start() {
    databases.start();
    record("postgres-ready");
    mailpit.start();
    record("mailpit-ready");
    keycloak.start();
    record("keycloak-ready");
    origin = ReferenceHttpsOrigin.start(productPort, identityPort);
    record("https-origin-ready");
    keycloakContract = new ReferenceKeycloakMaterializer(keycloakBaseUrl());
    ReferenceKeycloakMaterializer.Snapshot first =
        keycloakContract.materialize(origin().resolve("/invitations/completed").toString());
    ReferenceKeycloakMaterializer.Snapshot second =
        keycloakContract.materialize(origin().resolve("/invitations/completed").toString());
    if (!first.equals(second)) {
      throw new IllegalStateException("Reference Keycloak materialization was not idempotent.");
    }
    record("keycloak-contract-materialized-twice");
    startServices();
  }

  URI origin() {
    return origin.origin();
  }

  URI productInternal(String path) {
    return URI.create("http://127.0.0.1:" + productPort + path);
  }

  URI inviteInternal(String path) {
    return URI.create("http://127.0.0.1:" + invitePort + path);
  }

  URI identityInternal(String path) {
    return URI.create("http://127.0.0.1:" + identityPort + path);
  }

  URI billingInternal(String path) {
    return URI.create("http://127.0.0.1:" + billingPort + path);
  }

  ReferenceHttpsOrigin httpsOrigin() {
    return origin;
  }

  ReferenceMailpit mailpit() {
    return new ReferenceMailpit("http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025));
  }

  ReferenceKeycloakMaterializer keycloak() {
    return keycloakContract;
  }

  ReferenceDatabaseCluster databases() {
    return databases;
  }

  void record(String milestone) {
    milestones.add(Instant.now() + " " + milestone);
  }

  private void startServices() {
    ReferenceProcess identity =
        start(
            "identity",
            "identity",
            identityPort,
            environment(
                "identity",
                Map.ofEntries(
                    Map.entry("IDENTITY_PORT", Integer.toString(identityPort)),
                    Map.entry("IDENTITY_SESSION_MODE", "production"),
                    Map.entry("IDENTITY_ACCESS_COOKIE_NAME", "__Host-cardo.session"),
                    Map.entry("IDENTITY_REFRESH_COOKIE_NAME", "__Secure-cardo.refresh"),
                    Map.entry("IDENTITY_CSRF_COOKIE_NAME", "__Host-cardo.csrf"),
                    Map.entry("IDENTITY_REFRESH_COOKIE_PATH", "/api/v1/identity/sessions/current"),
                    Map.entry("IDENTITY_SESSION_SECURE", "true"),
                    Map.entry("KEYCLOAK_CLIENT_ID", "cardo-identity"),
                    Map.entry(
                        "KEYCLOAK_CLIENT_SECRET",
                        ReferenceKeycloakMaterializer.IDENTITY_RUNTIME_SECRET),
                    Map.entry(
                        "KEYCLOAK_IDENTITY_AUTHORIZATION_CLIENT_SECRET",
                        ReferenceKeycloakMaterializer.IDENTITY_CATALOG_SECRET),
                    Map.entry(
                        "KEYCLOAK_CREDENTIAL_SETUP_CLIENT_ID",
                        ReferenceKeycloakMaterializer.BROWSER_CLIENT),
                    Map.entry(
                        "KEYCLOAK_CREDENTIAL_SETUP_REDIRECT_URI",
                        origin().resolve("/invitations/completed").toString()),
                    Map.entry("IDENTITY_OPERATION_DISPATCH_DELAY", "100ms"),
                    Map.entry("IDENTITY_OPERATION_POLL_DELAY", "100ms"),
                    Map.entry("IDENTITY_OPERATION_RETRY_BASE_DELAY", "100ms"),
                    Map.entry("IDENTITY_PROVIDER_MUTATION_DISPATCH_DELAY", "100ms"),
                    Map.entry("IDENTITY_PROVIDER_MUTATION_RETRY_BASE_DELAY", "100ms"))));
    identity.awaitReady(local(identityPort, "/actuator/health/readiness"), STARTUP_TIMEOUT);
    record("identity-ready");

    ReferenceProcess invite =
        start(
            "invite",
            "invite",
            invitePort,
            environment(
                "invite",
                Map.ofEntries(
                    Map.entry("INVITE_PORT", Integer.toString(invitePort)),
                    Map.entry("KEYCLOAK_INVITE_CLIENT_ID", "cardo-invite"),
                    Map.entry(
                        "KEYCLOAK_INVITE_CLIENT_SECRET",
                        ReferenceKeycloakMaterializer.INVITE_SECRET),
                    Map.entry("IDENTITY_BASE_URL", "http://127.0.0.1:" + identityPort + "/api/v1"),
                    Map.entry(
                        "INVITE_PRODUCT_CLIENT_IDS", ReferenceContract.PRODUCT_OUTBOUND_CLIENT),
                    Map.entry("SMTP_HOST", mailpit.getHost()),
                    Map.entry("SMTP_PORT", Integer.toString(mailpit.getMappedPort(1025))),
                    Map.entry("INVITATION_COMPLETION_DISPATCH_DELAY", "100ms"),
                    Map.entry("INVITATION_COMPLETION_POLL_DELAY", "100ms"),
                    Map.entry("INVITATION_COMPLETION_RETRY_BASE_DELAY", "100ms"))));
    invite.awaitReady(local(invitePort, "/actuator/health/readiness"), STARTUP_TIMEOUT);
    record("invite-ready");

    ReferenceProcess billing =
        start(
            "billing",
            "billing",
            billingPort,
            environment("billing", Map.of("BILLING_PORT", Integer.toString(billingPort))));
    billing.awaitReady(local(billingPort, "/actuator/health/readiness"), STARTUP_TIMEOUT);
    record("billing-ready");

    ReferenceProcess product =
        start(
            "product",
            "integration/reference-stack",
            productPort,
            environment(
                "product",
                Map.ofEntries(
                    Map.entry("REFERENCE_PRODUCT_PORT", Integer.toString(productPort)),
                    Map.entry(
                        "REFERENCE_PRODUCT_CLIENT_SECRET",
                        ReferenceKeycloakMaterializer.PRODUCT_SECRET),
                    Map.entry(
                        "REFERENCE_PRODUCT_OUTBOUND_CLIENT_SECRET",
                        ReferenceKeycloakMaterializer.PRODUCT_OUTBOUND_SECRET),
                    Map.entry("REFERENCE_CONTROL_SECRET", CONTROL_SECRET),
                    Map.entry(
                        "REFERENCE_ACCEPT_URL", origin().resolve("/invitations/accept").toString()),
                    Map.entry(
                        "KEYCLOAK_INTROSPECTION_URI",
                        keycloakBaseUrl()
                            + "/realms/"
                            + ReferenceKeycloakMaterializer.REALM
                            + "/protocol/openid-connect/token/introspect"),
                    Map.entry("IDENTITY_BASE_URL", "http://127.0.0.1:" + identityPort + "/api/v1"),
                    Map.entry("INVITE_BASE_URL", "http://127.0.0.1:" + invitePort + "/api/v1"),
                    Map.entry("BILLING_BASE_URL", "http://127.0.0.1:" + billingPort + "/api/v1"),
                    Map.entry("REFERENCE_COMMANDS_DISPATCH_DELAY", "100ms"))));
    product.awaitReady(local(productPort, "/actuator/health/readiness"), STARTUP_TIMEOUT);
    record("product-ready");
  }

  private ReferenceProcess start(
      String name, String module, int port, Map<String, String> environment) {
    Path jar = checkout.resolve(module + "/target/" + artifact(name) + "-" + version + ".jar");
    ReferenceProcess process = ReferenceProcess.start(name, jar, environment);
    processes.add(process);
    return process;
  }

  private String artifact(String name) {
    return "product".equals(name) ? "integration-reference-stack" : name;
  }

  private Map<String, String> environment(String service, Map<String, String> additions) {
    ReferenceDatabaseCluster.Database database = databases.database(service);
    Map<String, String> environment = new LinkedHashMap<>();
    environment.put("JAVA_TOOL_OPTIONS", "-XX:MaxRAMPercentage=20 -XX:ActiveProcessorCount=2");
    environment.put("SPRING_DATASOURCE_URL", databases.jdbcUrl(database.name()));
    environment.put("SPRING_DATASOURCE_USERNAME", database.application());
    environment.put("SPRING_DATASOURCE_PASSWORD", database.password());
    environment.put("KEYCLOAK_BASE_URL", keycloakBaseUrl());
    environment.put("KEYCLOAK_REALM", ReferenceKeycloakMaterializer.REALM);
    environment.put(
        "KEYCLOAK_ISSUER_URI",
        keycloakBaseUrl() + "/realms/" + ReferenceKeycloakMaterializer.REALM);
    environment.putAll(additions);
    return Map.copyOf(environment);
  }

  String keycloakBaseUrl() {
    return "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080);
  }

  private static URI local(int port, String path) {
    return URI.create("http://127.0.0.1:" + port + path);
  }

  private static int freePort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      socket.setReuseAddress(false);
      return socket.getLocalPort();
    } catch (IOException failure) {
      throw new IllegalStateException("Could not reserve a reference port.", failure);
    }
  }

  @Override
  public void close() {
    RuntimeException failure = null;
    try {
      Files.createDirectories(diagnostics);
      Files.writeString(
          diagnostics.resolve("summary.txt"),
          ReferenceDiagnostics.sanitize(String.join(System.lineSeparator(), milestones)),
          StandardCharsets.UTF_8);
      for (ReferenceProcess process : processes) {
        process.writeDiagnostics(diagnostics);
      }
    } catch (RuntimeException | IOException diagnosticsFailure) {
      failure =
          new IllegalStateException("Could not write reference diagnostics.", diagnosticsFailure);
    }
    for (int index = processes.size() - 1; index >= 0; index--) {
      failure = close(processes.get(index), failure);
    }
    failure = close(origin, failure);
    failure = close(keycloak, failure);
    failure = close(mailpit, failure);
    failure = close(databases, failure);
    failure = close(network, failure);
    if (failure != null) {
      throw failure;
    }
  }

  private RuntimeException close(AutoCloseable resource, RuntimeException prior) {
    if (resource == null) {
      return prior;
    }
    try {
      resource.close();
    } catch (Exception closeFailure) {
      RuntimeException wrapped =
          new IllegalStateException("Reference teardown failed.", closeFailure);
      if (prior != null) {
        prior.addSuppressed(wrapped);
        return prior;
      }
      return wrapped;
    }
    return prior;
  }
}
