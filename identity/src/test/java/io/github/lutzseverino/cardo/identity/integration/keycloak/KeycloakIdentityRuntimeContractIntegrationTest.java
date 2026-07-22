package io.github.lutzseverino.cardo.identity.integration.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jwt.JWTParser;
import io.github.lutzseverino.cardo.authorization.grant.ClientRoleAssignment;
import io.github.lutzseverino.cardo.authorization.grant.ClientRoleRevocation;
import io.github.lutzseverino.cardo.authorization.grant.EffectiveGrant;
import io.github.lutzseverino.cardo.authorization.grant.GrantPlan;
import io.github.lutzseverino.cardo.authorization.grant.GrantProcessor;
import io.github.lutzseverino.cardo.authorization.grant.GrantReceipt;
import io.github.lutzseverino.cardo.authorization.grant.GrantReceiptStatus;
import io.github.lutzseverino.cardo.authorization.grant.GrantedResource;
import io.github.lutzseverino.cardo.authorization.grant.Grants;
import io.github.lutzseverino.cardo.authorization.grant.ResourceActionAssignment;
import io.github.lutzseverino.cardo.authorization.grant.ResourceGrantQuery;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakAuthorizationClient;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakAuthorizationException;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakRealmAdminClient;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakRequestingPartyTokenClient;
import io.github.lutzseverino.cardo.authorization.resource.AuthorizationResource;
import io.github.lutzseverino.cardo.identity.model.AuthenticatedPrincipal;
import io.github.lutzseverino.cardo.identity.model.AuthenticationMethod;
import io.github.lutzseverino.cardo.identity.model.AuthorizationTokenResult;
import io.github.lutzseverino.cardo.identity.model.UserStatus;
import io.github.lutzseverino.cardo.identity.provider.IdentityProvider;
import io.github.lutzseverino.cardo.identity.reader.AuthenticatedPrincipalReader;
import io.github.lutzseverino.cardo.identity.service.AuthenticationService;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class KeycloakIdentityRuntimeContractIntegrationTest {

  @Test
  void validatesAndRepairsARealProviderWithoutGrantingRuntimeDefinitionWrites() throws Exception {
    try (DisposableKeycloakProvisioner keycloak = new DisposableKeycloakProvisioner()) {
      keycloak.start();
      keycloak.bootstrapRealm();
      DisposableKeycloakProvisioner.ContractSnapshot first = keycloak.materializeContract();
      DisposableKeycloakProvisioner.ContractSnapshot second = keycloak.materializeContract();
      assertThat(second).isEqualTo(first);
      assertThat(second.clientCounts())
          .containsEntry("cardo-identity", 1)
          .containsEntry("identity", 1)
          .containsEntry("setup", 1)
          .containsEntry("billing", 1);
      assertThat(second.mapperCounts())
          .containsEntry("cardo-identity", 1)
          .containsEntry("identity", 1)
          .containsEntry("billing", 1);
      assertThat(second.identityRoles())
          .containsExactlyInAnyOrder("profile:read", "profile:write", "user:provision");
      assertThat(second.realmManagementGrants())
          .containsExactlyInAnyOrder("manage-users", "view-clients");
      assertThat(second.authorizationClientGrants()).containsExactly("uma_protection");
      RestClient.Builder rest = RestClient.builder();
      KeycloakClientCredentialsTokenProvider runtimeTokens = keycloak.runtimeTokens(rest);
      KeycloakClientCredentialsTokenProvider catalogTokens = keycloak.catalogTokens(rest);
      KeycloakLegacyStartupRepair repair =
          new KeycloakLegacyStartupRepair(keycloak.properties(false), runtimeTokens, rest.clone());
      KeycloakIdentityRuntimeContract validator =
          new KeycloakIdentityRuntimeContract(
              keycloak.properties(false), runtimeTokens, catalogTokens, repair, rest);

      String runtimeToken = runtimeTokens.clientCredentialsToken();
      assertThat(keycloak.realmManagementRoles(runtimeToken))
          .containsExactlyInAnyOrder("manage-users", "query-clients", "view-clients");
      assertThat(keycloak.runtimeClientRoles(runtimeToken)).isEmpty();
      assertThat(keycloak.authorizationClientRoles(runtimeToken)).isEmpty();
      assertThat(keycloak.authorizationClientRoles(catalogTokens.clientCredentialsToken()))
          .containsExactly("uma_protection");
      assertThatCode(validator::validate).doesNotThrowAnyException();
      KeycloakIdentityRuntimeContract swappedCredentials =
          new KeycloakIdentityRuntimeContract(
              keycloak.properties(false), catalogTokens, runtimeTokens, repair, rest.clone());
      assertThatThrownBy(swappedCredentials::validate)
          .hasMessageContaining("runtime credential has an unexpected authorized party")
          .hasMessageContaining("Identity catalog credential has an unexpected authorized party");
      assertThat(keycloak.mapperDefinitionWriteStatus(runtimeToken)).isEqualTo(403);
      assertThat(keycloak.roleDefinitionWriteStatus(runtimeToken)).isEqualTo(403);
      RealKeycloakIdentityProviderExercise.verify(
          keycloak.properties(false), runtimeTokens, rest.clone());

      String userId = keycloak.createUser();
      KeycloakAuthorizationClient authorization =
          new KeycloakAuthorizationClient(
              keycloak.properties(false).baseUrl(),
              keycloak.properties(false).realm(),
              "identity",
              rest.clone(),
              catalogTokens::clientCredentialsToken,
              runtimeTokens::clientCredentialsToken);
      authorization.ensureClientRolesAssigned(
          new ClientRoleAssignment("identity", userId, List.of("profile:read")));
      authorization.removeClientRoles(
          new ClientRoleRevocation("identity", userId, List.of("profile:read")));
      var resource =
          authorization.ensureResource(
              new AuthorizationResource(
                  "identity", "identity:resource:1", "identity:resource", null, List.of("read")));
      assertThat(resource.id()).isNotBlank();
      keycloak.enableUserManagedAccess(catalogTokens.clientCredentialsToken(), resource.id());
      authorization.grantResourceActions(
          new ResourceActionAssignment("identity", resource.id(), userId, List.of("read")));
      var grants =
          authorization.findResourceActionGrants(
              ResourceGrantQuery.forResourceId("identity", resource.id(), userId));
      assertThat(grants).hasSize(1);
      authorization.revokeResourceActionGrant("identity", grants.getFirst().id());

      verifyDurableIdentityGrantAndAuthentication(
          keycloak, authorization, runtimeTokens, rest.clone());

      keycloak.deleteRole("profile:read");
      assertThatThrownBy(
              () ->
                  authorization.ensureClientRolesAssigned(
                      new ClientRoleAssignment("identity", userId, List.of("profile:read"))))
          .isInstanceOf(KeycloakAuthorizationException.class)
          .hasMessageContaining("Required Keycloak client role is missing");
      assertThat(keycloak.roleExists("profile:read")).isFalse();

      keycloak.deleteMapper("billing");
      assertThatThrownBy(validator::validate)
          .hasMessageContaining("client billing mapper cardo_user_id expected one definition")
          .hasMessageContaining("Identity role profile:read lookup returned HTTP 404");

      KeycloakLegacyStartupRepair runtimeRepair =
          new KeycloakLegacyStartupRepair(keycloak.properties(true), runtimeTokens, rest.clone());
      runtimeRepair.repair();
      assertThatThrownBy(validator::validate)
          .hasMessageContaining("client billing mapper cardo_user_id expected one definition")
          .hasMessageContaining("Identity role profile:read lookup returned HTTP 404");

      KeycloakClientCredentialsTokenProvider privileged =
          mock(KeycloakClientCredentialsTokenProvider.class);
      when(privileged.clientCredentialsToken()).thenReturn(keycloak.privilegedToken());
      KeycloakLegacyStartupRepair privilegedRepair =
          new KeycloakLegacyStartupRepair(keycloak.properties(true), privileged, rest.clone());
      privilegedRepair.repair();

      assertThatCode(validator::validate).doesNotThrowAnyException();
      assertThatCode(privilegedRepair::repair).doesNotThrowAnyException();
      assertThatCode(validator::validate).doesNotThrowAnyException();
    }
  }

  private void verifyDurableIdentityGrantAndAuthentication(
      DisposableKeycloakProvisioner keycloak,
      KeycloakAuthorizationClient authorization,
      KeycloakClientCredentialsTokenProvider runtimeTokens,
      RestClient.Builder rest)
      throws Exception {
    String subject = keycloak.sessionUserId();
    UUID profileId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    AuthorizationResource profile =
        new AuthorizationResource(
            "identity", "identity:profile:" + profileId, "identity:profile", null, List.of("read"));
    GrantPlan plan = GrantPlan.builder().grantActions(subject, profile, List.of("read")).build();

    try (PostgreSQLContainer postgres =
        new PostgreSQLContainer("postgres:17.5-alpine")
            .withDatabaseName("identity")
            .withUsername("identity")
            .withPassword("identity")) {
      postgres.start();
      GrantReceipt receipt = stageAndApply(postgres, authorization, keycloak, plan, profile);
      assertThat(receipt.status()).isEqualTo(GrantReceiptStatus.APPLIED);
    }

    IdentityProvider provider =
        new KeycloakIdentityProvider(
            keycloak.properties(false),
            new KeycloakRealmAdminClient(
                keycloak.properties(false).baseUrl(),
                keycloak.properties(false).realm(),
                rest.clone(),
                runtimeTokens::clientCredentialsToken),
            runtimeTokens,
            rest.clone());
    AuthenticatedPrincipalReader principals = mock(AuthenticatedPrincipalReader.class);
    when(principals.findByKeycloakSubject(
            org.mockito.ArgumentMatchers.eq(subject),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.eq(AuthenticationMethod.PASSWORD),
            org.mockito.ArgumentMatchers.any(OffsetDateTime.class)))
        .thenAnswer(
            invocation ->
                Optional.of(
                    new AuthenticatedPrincipal(
                        invocation.getArgument(1),
                        profileId,
                        subject,
                        "session-user@example.test",
                        "Session User",
                        null,
                        UserStatus.ACTIVE,
                        true,
                        OffsetDateTime.now().minusDays(1),
                        OffsetDateTime.now(),
                        AuthenticationMethod.PASSWORD,
                        UUID.randomUUID(),
                        invocation.getArgument(3))));
    List<Map<String, Object>> capturedPermissions = new ArrayList<>();
    var authorizationTokens =
        (io.github.lutzseverino.cardo.identity.reader.AuthorizationTokenReader)
            token -> {
              try {
                var claims = JWTParser.parse(token).getJWTClaimsSet();
                assertThat(claims.getAudience()).containsExactly("identity");
                Map<String, Object> authorizationClaim = claims.getJSONObjectClaim("authorization");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> permissions =
                    (List<Map<String, Object>>) authorizationClaim.get("permissions");
                capturedPermissions.addAll(permissions);
                return new AuthorizationTokenResult(
                    claims.getSubject(),
                    OffsetDateTime.ofInstant(
                        claims.getExpirationTime().toInstant(), ZoneOffset.UTC),
                    List.of(
                        new EffectiveGrant(
                            new GrantedResource("identity:profile", profileId.toString()),
                            List.of("read"))));
              } catch (java.text.ParseException exception) {
                throw new AssertionError(exception);
              }
            };
    AuthenticationService authentication =
        new AuthenticationService(
            provider,
            principals,
            new KeycloakRequestingPartyTokenClient(
                keycloak.properties(false).baseUrl(),
                keycloak.properties(false).realm(),
                rest.clone()),
            authorizationTokens);

    var session =
        authentication.authenticate("session-user@example.test", "S3cure-cardo-password!");

    assertThat(session.authentication().grants())
        .containsExactly(
            new EffectiveGrant(
                new GrantedResource("identity:profile", profileId.toString()), List.of("read")));
    assertThat(capturedPermissions.stream().filter(permission -> permission.containsKey("scopes")))
        .singleElement()
        .satisfies(
            permission -> {
              assertThat(permission.get("rsname")).isEqualTo(profile.name());
              assertThat(permission.get("scopes")).isEqualTo(List.of("read"));
            });
  }

  private GrantReceipt stageAndApply(
      PostgreSQLContainer postgres,
      KeycloakAuthorizationClient authorization,
      DisposableKeycloakProvisioner keycloak,
      GrantPlan plan,
      AuthorizationResource resource)
      throws Exception {
    PGSimpleDataSource dataSource = new PGSimpleDataSource();
    dataSource.setURL(postgres.getJdbcUrl());
    dataSource.setUser(postgres.getUsername());
    dataSource.setPassword(postgres.getPassword());
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("create schema identity_events");
    jdbc.execute(
        """
        create table identity_events.grant_receipt (
          id uuid primary key,
          status varchar(16) not null,
          failure_code varchar(128),
          attempt_count integer not null,
          created_at timestamp with time zone not null,
          updated_at timestamp with time zone not null)
        """);

    Class<?> storeType =
        Class.forName("io.github.lutzseverino.cardo.authorization.grant.GrantReceiptStore");
    // Keep Authorization's receipt store package-private in production. Reflection is confined to
    // this cross-module integration fixture so it can prove the public Grants contract without
    // widening the runtime API solely for test assembly.
    Constructor<?> storeConstructor =
        storeType.getDeclaredConstructor(
            org.springframework.jdbc.core.JdbcOperations.class, String.class);
    storeConstructor.setAccessible(true);
    Object store = storeConstructor.newInstance(jdbc, "identity_events");
    Constructor<Grants> grantsConstructor =
        Grants.class.getDeclaredConstructor(ApplicationEventPublisher.class, storeType);
    grantsConstructor.setAccessible(true);
    List<Object> publications = new ArrayList<>();
    Grants grants =
        grantsConstructor.newInstance((ApplicationEventPublisher) publications::add, store);
    TransactionTemplate transactions =
        new TransactionTemplate(new DataSourceTransactionManager(dataSource));

    GrantReceipt pending = transactions.execute(ignored -> grants.stage(plan));
    assertThat(pending.status()).isEqualTo(GrantReceiptStatus.PENDING);
    assertThat(grants.find(pending.id())).contains(pending);
    assertThat(publications).hasSize(1);

    var created = authorization.ensureResource(resource);
    keycloak.enableUserManagedAccess(
        keycloak.catalogTokens(RestClient.builder()).clientCredentialsToken(), created.id());
    new GrantProcessor(authorization).apply(plan);
    Method markApplied = storeType.getDeclaredMethod("markApplied", UUID.class);
    markApplied.setAccessible(true);
    markApplied.invoke(store, pending.id());

    AuthorizationResource foreignResource =
        new AuthorizationResource(
            "polity", "polity:profile:foreign", "polity:profile", null, List.of("read"));
    GrantPlan foreignPlan =
        GrantPlan.builder()
            .grantActions("foreign-subject", foreignResource, List.of("read"))
            .build();
    GrantReceipt rejected = transactions.execute(ignored -> grants.stage(foreignPlan));
    assertThatThrownBy(() -> new GrantProcessor(authorization).apply(foreignPlan))
        .isInstanceOf(KeycloakAuthorizationException.class)
        .hasMessageContaining("bound to resource server identity")
        .hasMessageContaining("polity");
    Method recordFailure = storeType.getDeclaredMethod("recordFailure", UUID.class, int.class);
    recordFailure.setAccessible(true);
    assertThat(recordFailure.invoke(store, rejected.id(), 1)).isEqualTo(true);
    assertThat(grants.find(rejected.id()))
        .contains(
            new GrantReceipt(
                rejected.id(), GrantReceiptStatus.FAILED, "provider_application_failed"));

    return grants.find(pending.id()).orElseThrow();
  }
}
