package io.github.lutzseverino.cardo.identity.client.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import io.github.lutzseverino.cardo.identity.client.IdentityOperation;
import io.github.lutzseverino.cardo.identity.client.IdentityOperationStatus;
import io.github.lutzseverino.cardo.identity.client.IdentityUser;
import io.github.lutzseverino.cardo.identity.client.IdentityUserStatus;
import io.github.lutzseverino.cardo.identity.client.IdentityUsersClient;
import io.github.lutzseverino.cardo.identity.client.ProvisionalUser;
import io.github.lutzseverino.cardo.identity.client.http.generated.CreateProvisionalUserRequest;
import io.github.lutzseverino.cardo.identity.client.http.generated.SearchUsersRequest;
import io.github.lutzseverino.cardo.identity.client.http.generated.UserResponse;
import io.github.lutzseverino.cardo.identity.client.http.generated.UserStatus;
import io.github.lutzseverino.cardo.identity.client.http.generated.api.UsersApi;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

class HttpIdentityUsersClientTest {

  private final ApplicationContextRunner baseContext =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(IdentityClientAutoConfiguration.class))
          .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
          .withBean(
              KeycloakClientCredentialsTokenProvider.class,
              () -> mock(KeycloakClientCredentialsTokenProvider.class));
  private final ApplicationContextRunner context =
      baseContext.withPropertyValues(
          "cardo.identity.client.base-url=http://identity.test/api/v1",
          "cardo.identity.client.service-token-scope=identity");

  @Test
  void autoConfiguresTheStableClientContract() {
    context.run(
        application -> {
          assertThat(application).hasSingleBean(IdentityUsersClient.class);
          IdentityClientProperties properties = application.getBean(IdentityClientProperties.class);
          assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
          assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(2));
          assertThat(properties.serviceTokenScope()).isEqualTo("identity");
        });
  }

  @Test
  void backsOffForACustomClientWithoutHttpProperties() {
    IdentityUsersClient customClient = mock(IdentityUsersClient.class);

    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(IdentityClientAutoConfiguration.class))
        .withBean(IdentityUsersClient.class, () -> customClient)
        .run(
            application -> {
              assertThat(application).hasNotFailed().hasSingleBean(IdentityUsersClient.class);
              assertThat(application.getBean(IdentityUsersClient.class)).isSameAs(customClient);
              assertThat(application).doesNotHaveBean(IdentityClientProperties.class);
            });
  }

  @Test
  void rejectsMissingStandardClientPropertiesAtStartup() {
    baseContext.run(application -> assertThat(application).hasFailed());
  }

  @Test
  void bindsClientTimeoutOverrides() {
    context
        .withPropertyValues(
            "cardo.identity.client.connect-timeout=500ms", "cardo.identity.client.read-timeout=3s")
        .run(
            application -> {
              IdentityClientProperties properties =
                  application.getBean(IdentityClientProperties.class);
              assertThat(properties.connectTimeout()).isEqualTo(Duration.ofMillis(500));
              assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(3));
            });
  }

  @Test
  void rejectsAnUnboundedClientTimeout() {
    context
        .withPropertyValues("cardo.identity.client.read-timeout=0s")
        .run(application -> assertThat(application).hasFailed());
  }

  @Test
  void rejectsMissingAndBlankServiceTokenScopesAtStartup() {
    baseContext
        .withPropertyValues("cardo.identity.client.base-url=http://identity.test/api/v1")
        .run(application -> assertThat(application).hasFailed());
    context
        .withPropertyValues("cardo.identity.client.service-token-scope=  ")
        .run(application -> assertThat(application).hasFailed());
  }

  @Test
  void requestsOnlyTheConfiguredIdentityServiceTokenScope() {
    context
        .withPropertyValues("cardo.identity.client.base-url=http://localhost:1/api/v1")
        .run(
            application -> {
              KeycloakClientCredentialsTokenProvider tokens =
                  application.getBean(KeycloakClientCredentialsTokenProvider.class);
              when(tokens.clientCredentialsToken("identity")).thenReturn("identity-token");

              assertThatThrownBy(
                      () ->
                          application
                              .getBean(IdentityUsersClient.class)
                              .searchByAuthorizationSubjects(Set.of("subject-1")))
                  .isInstanceOf(RuntimeException.class);
              verify(tokens).clientCredentialsToken("identity");
              verify(tokens, org.mockito.Mockito.never()).clientCredentialsToken();
            });
  }

  @Test
  void registersAutoConfigurationForDiscovery() {
    assertThat(
            ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())
                .getCandidates())
        .contains(IdentityClientAutoConfiguration.class.getName());
  }

  @Test
  void mapsProvisionalUserAcrossTheGeneratedClientBoundary() {
    UsersApi users = mock(UsersApi.class);
    HttpIdentityUsersClient client = new HttpIdentityUsersClient(users);
    UUID userId = UUID.randomUUID();
    when(users.createProvisionalUser(any(CreateProvisionalUserRequest.class)))
        .thenReturn(new UserResponse().id(userId).authorizationSubject("subject-1"));

    assertThat(client.createProvisional("employee@example.com"))
        .isEqualTo(new ProvisionalUser(userId, "subject-1"));

    ArgumentCaptor<CreateProvisionalUserRequest> input =
        ArgumentCaptor.forClass(CreateProvisionalUserRequest.class);
    verify(users).createProvisionalUser(input.capture());
    assertThat(input.getValue().getEmail()).isEqualTo("employee@example.com");
  }

  @Test
  void mapsUsersAndSearchCriteriaAcrossTheGeneratedClientBoundary() {
    UsersApi users = mock(UsersApi.class);
    HttpIdentityUsersClient client = new HttpIdentityUsersClient(users);
    UUID userId = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.parse("2026-06-11T12:00:00Z");
    when(users.searchUsers(any(SearchUsersRequest.class)))
        .thenReturn(
            List.of(
                new UserResponse()
                    .id(userId)
                    .authorizationSubject("subject-1")
                    .email("dentist@example.com")
                    .name("Dentist")
                    .avatarUrl(URI.create("https://example.com/avatar.png"))
                    .status(UserStatus.ACTIVE)
                    .emailVerified(true)
                    .createdAt(now)
                    .updatedAt(now)));

    assertThat(client.searchByAuthorizationSubjects(Set.of("subject-1")))
        .containsExactly(
            new IdentityUser(
                userId,
                "subject-1",
                "dentist@example.com",
                "Dentist",
                "https://example.com/avatar.png",
                IdentityUserStatus.ACTIVE,
                true,
                now,
                now));

    ArgumentCaptor<SearchUsersRequest> request = ArgumentCaptor.forClass(SearchUsersRequest.class);
    verify(users).searchUsers(request.capture());
    assertThat(request.getValue().getAuthorizationSubjects()).containsExactly("subject-1");
  }

  @Test
  void mapsInspectableProvisionalDeletionOperations() {
    UsersApi users = mock(UsersApi.class);
    HttpIdentityUsersClient client = new HttpIdentityUsersClient(users);
    UUID userId = UUID.randomUUID();
    UUID operationId = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.parse("2026-07-17T12:00:00Z");
    io.github.lutzseverino.cardo.identity.client.http.generated.IdentityOperationResponse response =
        new io.github.lutzseverino.cardo.identity.client.http.generated.IdentityOperationResponse()
            .id(operationId)
            .userId(userId)
            .status(
                io.github.lutzseverino.cardo.identity.client.http.generated.IdentityOperationStatus
                    .REQUESTED)
            .attemptCount(0)
            .createdAt(now)
            .updatedAt(now);
    when(users.cancelProvisionalUser(userId)).thenReturn(response);
    when(users.getProvisionalDeletion(userId)).thenReturn(response);

    IdentityOperation expected =
        new IdentityOperation(
            operationId, userId, IdentityOperationStatus.REQUESTED, 0, null, null, null, now, now);
    assertThat(client.cancelProvisional(userId)).isEqualTo(expected);
    assertThat(client.getProvisionalDeletion(userId)).isEqualTo(expected);
  }
}
