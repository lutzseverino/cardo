package com.odonta.authorization.grant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;

class AuthorizationPlanRecoveryTest {

  @Test
  void resubmitsFailedPublications() {
    FailedEventPublications publications = mock(FailedEventPublications.class);

    new AuthorizationPlanRecovery(publications).retryFailed();

    ArgumentCaptor<ResubmissionOptions> options =
        ArgumentCaptor.forClass(ResubmissionOptions.class);
    verify(publications).resubmit(options.capture());
    assertThat(options.getValue().getFilter())
        .accepts(publication(mock(GrantPlan.class)), publication(mock(RevocationPlan.class)))
        .rejects(publication(new Object()));
  }

  private EventPublication publication(Object event) {
    EventPublication publication = mock(EventPublication.class);
    org.mockito.Mockito.when(publication.getEvent()).thenReturn(event);
    return publication;
  }
}
