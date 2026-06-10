package com.odonta.authorization.grant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.FailedEventPublications;

class GrantRecoveryTest {

  @Test
  void resubmitsFailedPublications() {
    FailedEventPublications publications = mock(FailedEventPublications.class);

    new GrantRecovery(publications).retryFailed();

    verify(publications).resubmit(any());
  }
}
