package com.odonta.identity.client.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odonta.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import com.odonta.identity.client.IdentityUser;
import com.odonta.identity.client.IdentityUserStatus;
import com.odonta.identity.client.IdentityUsersClient;
import com.odonta.identity.client.ProvisionalUser;
import com.odonta.identity.client.http.generated.CreateProvisionalUserRequest;
import com.odonta.identity.client.http.generated.SearchUsersRequest;
import com.odonta.identity.client.http.generated.UserResponse;
import com.odonta.identity.client.http.generated.UserStatus;
import com.odonta.identity.client.http.generated.UsersResponse;
import com.odonta.identity.client.http.generated.api.UsersApi;
import java.net.URI;
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

class HttpIdentityUsersClientTest {

  private final ApplicationContextRunner context =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(IdentityClientAutoConfiguration.class))
          .withBean(ObjectMapper.class)
          .withBean(
              KeycloakClientCredentialsTokenProvider.class,
              () -> mock(KeycloakClientCredentialsTokenProvider.class))
          .withPropertyValues("odonta.identity.client.base-url=http://identity.test/api/v1");

  @Test
  void autoConfiguresTheStableClientContract() {
    context.run(application -> assertThat(application).hasSingleBean(IdentityUsersClient.class));
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

    ArgumentCaptor<CreateProvisionalUserRequest> request =
        ArgumentCaptor.forClass(CreateProvisionalUserRequest.class);
    verify(users).createProvisionalUser(request.capture());
    assertThat(request.getValue().getEmail()).isEqualTo("employee@example.com");
  }

  @Test
  void mapsUsersAndSearchCriteriaAcrossTheGeneratedClientBoundary() {
    UsersApi users = mock(UsersApi.class);
    HttpIdentityUsersClient client = new HttpIdentityUsersClient(users);
    UUID userId = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.parse("2026-06-11T12:00:00Z");
    when(users.searchUsers(any(SearchUsersRequest.class)))
        .thenReturn(
            new UsersResponse()
                .users(
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
                            .updatedAt(now))));

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
}
