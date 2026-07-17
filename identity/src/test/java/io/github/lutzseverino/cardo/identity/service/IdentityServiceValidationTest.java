package io.github.lutzseverino.cardo.identity.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.lutzseverino.cardo.authorization.grant.Grants;
import io.github.lutzseverino.cardo.authorization.token.RequestingPartyTokenClient;
import io.github.lutzseverino.cardo.identity.authorization.IdentityGrantPlanner;
import io.github.lutzseverino.cardo.identity.mapper.UserApplicationMapperImpl;
import io.github.lutzseverino.cardo.identity.model.CreateUserInput;
import io.github.lutzseverino.cardo.identity.provider.IdentityProvider;
import io.github.lutzseverino.cardo.identity.reader.AuthenticatedPrincipalReader;
import io.github.lutzseverino.cardo.identity.reader.AuthorizationTokenGrantReader;
import io.github.lutzseverino.cardo.identity.repository.UserRepository;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

@SpringJUnitConfig(IdentityServiceValidationTest.Config.class)
class IdentityServiceValidationTest {

  @Autowired private UserRepository users;

  @Autowired private IdentityProvider identityProvider;

  @Autowired private Grants grants;

  @Autowired private AuthenticationService authenticationService;

  @Autowired private UserService userService;

  @Test
  void validatesRequestsAtTheServiceBoundary() {
    CreateUserInput input = new CreateUserInput("not-an-email", "short", "Owner");

    assertThatThrownBy(() -> userService.create(input))
        .isInstanceOf(ConstraintViolationException.class);

    verifyNoInteractions(users, identityProvider, grants);
  }

  @Test
  void validatesScalarParametersAtTheServiceBoundary() {
    assertThatThrownBy(() -> userService.getByEmail("not-an-email"))
        .isInstanceOf(ConstraintViolationException.class);

    assertThatThrownBy(() -> authenticationService.authenticate("not-an-email", "password"))
        .isInstanceOf(ConstraintViolationException.class);

    verifyNoInteractions(users, identityProvider, grants);
  }

  static class Config {

    @Bean
    static MethodValidationPostProcessor methodValidationPostProcessor() {
      return new MethodValidationPostProcessor();
    }

    @Bean
    UserRepository users() {
      return mock(UserRepository.class);
    }

    @Bean
    IdentityProvider identityProvider() {
      return mock(IdentityProvider.class);
    }

    @Bean
    Grants grants() {
      return mock(Grants.class);
    }

    @Bean
    IdentityGrantPlanner identityGrantPlanner() {
      return new IdentityGrantPlanner();
    }

    @Bean
    AuthenticatedPrincipalReader principals() {
      return mock(AuthenticatedPrincipalReader.class);
    }

    @Bean
    RequestingPartyTokenClient requestingPartyTokens() {
      return mock(RequestingPartyTokenClient.class);
    }

    @Bean
    AuthorizationTokenGrantReader tokenGrants() {
      return mock(AuthorizationTokenGrantReader.class);
    }

    @Bean
    UserService userService(
        UserRepository users,
        IdentityProvider identityProvider,
        Grants grants,
        IdentityGrantPlanner identityGrantPlanner) {
      return new UserService(
          users, new UserApplicationMapperImpl(), identityProvider, grants, identityGrantPlanner);
    }

    @Bean
    AuthenticationService authenticationService(
        IdentityProvider identityProvider,
        AuthenticatedPrincipalReader principals,
        RequestingPartyTokenClient requestingPartyTokens,
        AuthorizationTokenGrantReader tokenGrants) {
      return new AuthenticationService(
          identityProvider, principals, requestingPartyTokens, tokenGrants);
    }
  }
}
