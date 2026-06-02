package com.odonta.identity.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.odonta.authorization.sync.AuthorizationSyncService;
import com.odonta.identity.model.CreateUserCommand;
import com.odonta.identity.provider.IdentityProvider;
import com.odonta.identity.repository.UserRepository;
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

  @Autowired private AuthorizationSyncService authorizationSync;

  @Autowired private UserService userService;

  @Test
  void validatesCommandsAtTheServiceBoundary() {
    CreateUserCommand command = new CreateUserCommand("not-an-email", "short", "Owner");

    assertThatThrownBy(() -> userService.create(command))
        .isInstanceOf(ConstraintViolationException.class);

    verifyNoInteractions(users, identityProvider, authorizationSync);
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
    AuthorizationSyncService authorizationSync() {
      return mock(AuthorizationSyncService.class);
    }

    @Bean
    UserService userService(
        UserRepository users,
        IdentityProvider identityProvider,
        AuthorizationSyncService authorizationSync) {
      return new UserService(users, identityProvider, authorizationSync);
    }
  }
}
