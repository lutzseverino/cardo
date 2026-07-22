package io.github.lutzseverino.cardo.invite.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.identity.client.IdentityUsersClient;
import io.github.lutzseverino.cardo.identity.client.ProvisionalUser;
import io.github.lutzseverino.cardo.invite.model.CreateInvitationInput;
import io.github.lutzseverino.cardo.invite.provider.InvitationSender;
import io.github.lutzseverino.cardo.invite.repository.InvitationRepository;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
    properties = {
      "spring.jpa.hibernate.ddl-auto=validate",
      "cardo.invite.product-callers.allowed-client-ids=reference-product-outbound"
    })
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InvitationCreationPostgreSqlIntegrationTest {

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.5-alpine")
          .withDatabaseName("invite")
          .withUsername("invite")
          .withPassword("invite");

  @MockitoBean private IdentityUsersClient identityUsers;
  @MockitoBean private InvitationSender sender;
  @MockitoBean private JwtDecoder jwtDecoder;

  @Autowired private CreateInvitationWorkflow workflow;
  @Autowired private InvitationRepository invitations;

  @DynamicPropertySource
  static void configurePostgres(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    properties.add("spring.datasource.username", POSTGRES::getUsername);
    properties.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void persistsAndPublishesTheExactPortableReferenceInvitation() {
    UUID requestId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    UUID invitedBy = UUID.randomUUID();
    UUID invitedUserId = UUID.randomUUID();
    URI acceptUrl = URI.create("https://localhost:32777/invitations/accept");
    CreateInvitationInput input =
        new CreateInvitationInput(
            requestId,
            tenantId,
            "reference-product-outbound:tenant",
            "invited@reference.test",
            invitedBy,
            acceptUrl);
    when(identityUsers.createProvisional(input.email()))
        .thenReturn(new ProvisionalUser(invitedUserId, "invited-subject"));

    var created = workflow.create("reference-product-outbound", input);

    assertThat(created.invitation())
        .satisfies(
            invitation -> {
              assertThat(invitation.requestId()).isEqualTo(requestId);
              assertThat(invitation.tenantResourceType()).isEqualTo(input.tenantResourceType());
              assertThat(invitation.invitedUserId()).isEqualTo(invitedUserId);
            });
    assertThat(
            invitations.findProjectedByProductAndRequestId("reference-product-outbound", requestId))
        .isPresent();
    verify(sender, timeout(5_000)).send(eq(input.email()), startsWith(acceptUrl + "/"));
  }
}
