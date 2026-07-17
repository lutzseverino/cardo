package io.github.lutzseverino.cardo.invite.client.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import io.github.lutzseverino.cardo.invite.client.CreateInvitation;
import io.github.lutzseverino.cardo.invite.client.InvitationCompletion;
import io.github.lutzseverino.cardo.invite.client.InvitationCompletionStatus;
import io.github.lutzseverino.cardo.invite.client.InvitationGrant;
import io.github.lutzseverino.cardo.invite.client.InvitationStatus;
import io.github.lutzseverino.cardo.invite.client.InvitationToken;
import io.github.lutzseverino.cardo.invite.client.InvitationsClient;
import io.github.lutzseverino.cardo.invite.client.http.generated.CreateInvitationRequest;
import io.github.lutzseverino.cardo.invite.client.http.generated.CreateInvitationResponse;
import io.github.lutzseverino.cardo.invite.client.http.generated.InvitationCompletionResponse;
import io.github.lutzseverino.cardo.invite.client.http.generated.InvitationResponse;
import io.github.lutzseverino.cardo.invite.client.http.generated.InvitationTokenResponse;
import io.github.lutzseverino.cardo.invite.client.http.generated.api.InvitationTokensApi;
import io.github.lutzseverino.cardo.invite.client.http.generated.api.InvitationsApi;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class HttpInvitationsClientTest {

  private final ApplicationContextRunner context =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(InviteClientAutoConfiguration.class))
          .withBean(ObjectMapper.class)
          .withBean(
              KeycloakClientCredentialsTokenProvider.class,
              () -> mock(KeycloakClientCredentialsTokenProvider.class))
          .withPropertyValues("cardo.invite.client.base-url=http://invite.test/api/v1");

  @Test
  void autoConfiguresTheStableClientContract() {
    context.run(application -> assertThat(application).hasSingleBean(InvitationsClient.class));
  }

  @Test
  void registersAutoConfigurationForDiscovery() {
    assertThat(
            ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())
                .getCandidates())
        .contains(InviteClientAutoConfiguration.class.getName());
  }

  @Test
  void mapsCreationAcrossTheGeneratedClientBoundary() {
    InvitationsApi invitations = mock(InvitationsApi.class);
    HttpInvitationsClient client =
        new HttpInvitationsClient(invitations, mock(InvitationTokensApi.class));
    UUID requestId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    UUID inviterId = UUID.randomUUID();
    URI acceptUrlBase = URI.create("https://clinic.example.com/invitations");
    URI acceptUrl = URI.create("https://clinic.example.com/invitations/token-1");
    CreateInvitation input =
        new CreateInvitation(
            requestId,
            tenantId,
            "clinic:clinic",
            "employee@example.com",
            "clinic:employee",
            List.of(new InvitationGrant("clinic:clinic", "read")),
            inviterId,
            acceptUrlBase);
    when(invitations.createInvitation(any(CreateInvitationRequest.class)))
        .thenReturn(
            new CreateInvitationResponse()
                .invitation(response(requestId, tenantId, inviterId))
                .acceptUrl(acceptUrl));

    var created = client.create(input);

    assertThat(created.acceptUrl()).isEqualTo(acceptUrl);
    assertThat(created.invitation().status()).isEqualTo(InvitationStatus.PENDING);
    ArgumentCaptor<CreateInvitationRequest> request =
        ArgumentCaptor.forClass(CreateInvitationRequest.class);
    verify(invitations).createInvitation(request.capture());
    assertThat(request.getValue().getRequestId()).isEqualTo(requestId);
    assertThat(request.getValue().getAccessProfile()).isEqualTo("clinic:employee");
    assertThat(request.getValue().getAcceptUrlBase()).isEqualTo(acceptUrlBase);
    assertThat(request.getValue().getGrants())
        .singleElement()
        .satisfies(
            grant -> {
              assertThat(grant.getResourceType()).isEqualTo("clinic:clinic");
              assertThat(grant.getAction()).isEqualTo("read");
            });
  }

  @Test
  void mapsDurableIdentityCompletionAcrossTheGeneratedClientBoundary() {
    InvitationTokensApi tokens = mock(InvitationTokensApi.class);
    HttpInvitationsClient client = new HttpInvitationsClient(mock(InvitationsApi.class), tokens);
    UUID operationId = UUID.randomUUID();
    UUID invitationId = operationId;
    UUID userId = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.parse("2026-07-17T10:00:00Z");
    when(tokens.requestInvitationCompletion("token-1"))
        .thenReturn(
            new InvitationCompletionResponse()
                .id(operationId)
                .invitationId(invitationId)
                .invitedUserId(userId)
                .status(
                    io.github.lutzseverino.cardo.invite.client.http.generated
                        .InvitationCompletionStatus.REQUESTED)
                .attemptCount(0)
                .actionExpiresAt(now.plusHours(24))
                .createdAt(now)
                .updatedAt(now));

    assertThat(client.requestCompletion("token-1"))
        .isEqualTo(
            new InvitationCompletion(
                operationId,
                invitationId,
                userId,
                InvitationCompletionStatus.REQUESTED,
                0,
                null,
                now.plusHours(24),
                null,
                now,
                now));

    verify(tokens).requestInvitationCompletion("token-1");
  }

  @Test
  void mapsTheMinimalPublicTokenView() {
    InvitationTokensApi tokens = mock(InvitationTokensApi.class);
    HttpInvitationsClient client = new HttpInvitationsClient(mock(InvitationsApi.class), tokens);
    UUID invitationId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    OffsetDateTime expiresAt = OffsetDateTime.parse("2026-07-20T10:00:00Z");
    when(tokens.getInvitationByToken("token-1"))
        .thenReturn(
            new InvitationTokenResponse()
                .id(invitationId)
                .tenantId(tenantId)
                .tenantResourceType("clinic:clinic")
                .invitedEmail("employee@example.com")
                .expiresAt(expiresAt));

    assertThat(client.getByToken("token-1"))
        .isEqualTo(
            new InvitationToken(
                invitationId, tenantId, "clinic:clinic", "employee@example.com", expiresAt));
  }

  private InvitationResponse response(UUID requestId, UUID tenantId, UUID inviterId) {
    UUID invitationId = UUID.randomUUID();
    UUID invitedUserId = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.parse("2026-07-17T10:00:00Z");
    return new InvitationResponse()
        .id(invitationId)
        .requestId(requestId)
        .tenantId(tenantId)
        .tenantResourceType("clinic:clinic")
        .accessProfile("clinic:employee")
        .invitedEmail("employee@example.com")
        .invitedUserId(invitedUserId)
        .invitedBy(inviterId)
        .status(io.github.lutzseverino.cardo.invite.client.http.generated.InvitationStatus.PENDING)
        .expiresAt(now.plusDays(3))
        .createdAt(now)
        .updatedAt(now);
  }
}
