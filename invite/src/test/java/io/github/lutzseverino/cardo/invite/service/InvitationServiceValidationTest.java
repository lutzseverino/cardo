package io.github.lutzseverino.cardo.invite.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.lutzseverino.cardo.authorization.access.AccessProfileService;
import io.github.lutzseverino.cardo.authorization.grant.Grants;
import io.github.lutzseverino.cardo.authorization.spring.AuthenticatedUser;
import io.github.lutzseverino.cardo.identity.client.IdentityUsersClient;
import io.github.lutzseverino.cardo.invite.authorization.InvitationGrantPlanner;
import io.github.lutzseverino.cardo.invite.config.InvitationProperties;
import io.github.lutzseverino.cardo.invite.mapper.InvitationApplicationMapperImpl;
import io.github.lutzseverino.cardo.invite.model.CreateInvitationInput;
import io.github.lutzseverino.cardo.invite.repository.InvitationRepository;
import jakarta.validation.ConstraintViolationException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

@SpringJUnitConfig(InvitationServiceValidationTest.Config.class)
class InvitationServiceValidationTest {

  @Autowired private AccessProfileService accessProfiles;

  @Autowired private IdentityUsersClient identityUsers;

  @Autowired private InvitationRepository invitations;

  @Autowired private InvitationService invitationService;

  @Test
  void validatesRequestsAtTheServiceBoundary() {
    CreateInvitationInput input =
        new CreateInvitationInput(
            UUID.randomUUID(), "clinic", "employee@example.com", UUID.randomUUID());

    assertThatThrownBy(() -> invitationService.create(inviter(), input))
        .isInstanceOf(ConstraintViolationException.class);

    verifyNoInteractions(accessProfiles, identityUsers, invitations);
  }

  @Test
  void validatesScalarParametersAtTheServiceBoundary() {
    assertThatThrownBy(() -> invitationService.get("   "))
        .isInstanceOf(ConstraintViolationException.class);

    verifyNoInteractions(invitations);
  }

  private AuthenticatedUser inviter() {
    return new AuthenticatedUser(UUID.randomUUID(), "owner-subject", "Owner");
  }

  static class Config {

    @Bean
    static MethodValidationPostProcessor methodValidationPostProcessor() {
      return new MethodValidationPostProcessor();
    }

    @Bean
    AccessProfileService accessProfiles() {
      return mock(AccessProfileService.class);
    }

    @Bean
    Grants grants() {
      return mock(Grants.class);
    }

    @Bean
    InvitationGrantPlanner invitationGrantPlanner() {
      return new InvitationGrantPlanner();
    }

    @Bean
    EmailSender email() {
      return mock(EmailSender.class);
    }

    @Bean
    IdentityUsersClient identityUsers() {
      return mock(IdentityUsersClient.class);
    }

    @Bean
    InvitationProperties invitationProperties() {
      return new InvitationProperties(Duration.ofHours(72), "https://app.example.com");
    }

    @Bean
    InvitationRepository invitations() {
      return mock(InvitationRepository.class);
    }

    @Bean
    InvitationService invitationService(
        AccessProfileService accessProfiles,
        EmailSender email,
        Grants grants,
        IdentityUsersClient identityUsers,
        InvitationGrantPlanner invitationGrantPlanner,
        InvitationProperties invitationProperties,
        InvitationRepository invitations) {
      return new InvitationService(
          accessProfiles,
          email,
          grants,
          identityUsers,
          invitationGrantPlanner,
          new InvitationApplicationMapperImpl(),
          invitationProperties,
          invitations);
    }
  }
}
