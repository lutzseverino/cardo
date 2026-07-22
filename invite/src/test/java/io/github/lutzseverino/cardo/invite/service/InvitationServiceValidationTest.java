package io.github.lutzseverino.cardo.invite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.identity.client.IdentityUsersClient;
import io.github.lutzseverino.cardo.identity.client.ProvisionalUser;
import io.github.lutzseverino.cardo.invite.api.model.CreateInvitationRequest;
import io.github.lutzseverino.cardo.invite.config.InvitationProperties;
import io.github.lutzseverino.cardo.invite.mapper.InvitationApplicationMapperImpl;
import io.github.lutzseverino.cardo.invite.model.CreateInvitationInput;
import io.github.lutzseverino.cardo.invite.provider.InvitationDelivery;
import io.github.lutzseverino.cardo.invite.repository.InvitationRepository;
import io.github.lutzseverino.cardo.invite.workflow.CreateInvitationWorkflow;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

@SpringJUnitConfig(InvitationServiceValidationTest.Config.class)
class InvitationServiceValidationTest {

  @Autowired private IdentityUsersClient identityUsers;

  @Autowired private InvitationRepository invitations;

  @Autowired private InvitationService invitationService;

  @Autowired private CreateInvitationWorkflow createInvitation;

  @BeforeEach
  void resetCollaborators() {
    reset(identityUsers, invitations);
  }

  @Test
  void validatesRequestsAtTheServiceBoundary() {
    CreateInvitationInput input =
        new CreateInvitationInput(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "clinic",
            "employee@example.com",
            UUID.randomUUID(),
            URI.create("https://clinic.example.com/invitations"));

    assertThatThrownBy(() -> createInvitation.create("clinic", input))
        .isInstanceOf(ConstraintViolationException.class);

    verifyNoInteractions(identityUsers, invitations);
  }

  @Test
  void validatesScalarParametersAtTheServiceBoundary() {
    assertThatThrownBy(() -> invitationService.get("   "))
        .isInstanceOf(ConstraintViolationException.class);

    verifyNoInteractions(invitations);
  }

  @Test
  void rejectsAnAcceptanceUrlThatCannotSafelyReceiveTheTokenPath() {
    CreateInvitationInput input =
        new CreateInvitationInput(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "clinic:clinic",
            "employee@example.com",
            UUID.randomUUID(),
            URI.create("https://clinic.example.com/invitations?source=email"));

    assertThatThrownBy(() -> createInvitation.create("clinic", input))
        .isInstanceOf(ConstraintViolationException.class);

    verifyNoInteractions(identityUsers, invitations);
  }

  @Test
  void acceptsTheExactPortableReferenceInvitationAtEveryValidationBoundary() {
    UUID invitedUserId = UUID.randomUUID();
    CreateInvitationInput input =
        new CreateInvitationInput(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "reference-product-outbound:tenant",
            "invited@reference.test",
            UUID.randomUUID(),
            URI.create("https://localhost:32777/invitations/accept"));
    CreateInvitationRequest request =
        new CreateInvitationRequest()
            .requestId(input.requestId())
            .tenantId(input.tenantId())
            .tenantResourceType(input.tenantResourceType())
            .email(input.email())
            .invitedBy(input.invitedBy())
            .acceptUrlBase(input.acceptUrlBase());
    try (var validators = Validation.buildDefaultValidatorFactory()) {
      assertThat(validators.getValidator().validate(request)).isEmpty();
    }

    when(identityUsers.createProvisional(input.email()))
        .thenReturn(new ProvisionalUser(invitedUserId, "invited-subject"));
    when(invitations.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

    assertThatThrownBy(() -> createInvitation.create("reference-product-outbound", input))
        .isInstanceOf(ApiException.class)
        .isNotInstanceOf(ConstraintViolationException.class);
    verify(identityUsers).createProvisional(input.email());
    verify(invitations).saveAndFlush(any());
  }

  static class Config {

    @Bean
    static MethodValidationPostProcessor methodValidationPostProcessor() {
      return new MethodValidationPostProcessor();
    }

    @Bean
    InvitationDelivery invitationDelivery() {
      return mock(InvitationDelivery.class);
    }

    @Bean
    IdentityUsersClient identityUsers() {
      return mock(IdentityUsersClient.class);
    }

    @Bean
    InvitationProperties invitationProperties() {
      return new InvitationProperties(Duration.ofHours(72), Duration.ofMinutes(5));
    }

    @Bean
    InvitationRepository invitations() {
      return mock(InvitationRepository.class);
    }

    @Bean
    InvitationService invitationService(
        InvitationDelivery invitationDelivery,
        InvitationProperties invitationProperties,
        InvitationRepository invitations) {
      return new InvitationService(
          invitationDelivery,
          new InvitationApplicationMapperImpl(),
          invitationProperties,
          invitations);
    }

    @Bean
    CreateInvitationWorkflow createInvitationWorkflow(
        IdentityUsersClient identityUsers, InvitationService invitations) {
      return new CreateInvitationWorkflow(identityUsers, invitations);
    }
  }
}
