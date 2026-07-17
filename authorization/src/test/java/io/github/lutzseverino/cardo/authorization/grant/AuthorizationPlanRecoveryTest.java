package io.github.lutzseverino.cardo.authorization.grant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;

class AuthorizationPlanRecoveryTest {

  @Test
  void resubmitsStaleIncompletePublications() {
    IncompleteEventPublications publications = mock(IncompleteEventPublications.class);

    new AuthorizationPlanRecovery(publications, Duration.ofMinutes(1)).retryIncomplete();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Predicate<EventPublication>> filter = ArgumentCaptor.forClass(Predicate.class);
    verify(publications).resubmitIncompletePublications(filter.capture());
    assertThat(filter.getValue())
        .accepts(
            publication(mock(GrantPlan.class), Instant.EPOCH),
            publication(mock(RevocationPlan.class), Instant.EPOCH))
        .rejects(
            publication(mock(GrantPlan.class), Instant.now()),
            publication(new Object(), Instant.EPOCH));
  }

  private EventPublication publication(Object event, Instant publicationDate) {
    EventPublication publication = mock(EventPublication.class);
    org.mockito.Mockito.when(publication.getEvent()).thenReturn(event);
    org.mockito.Mockito.when(publication.getPublicationDate()).thenReturn(publicationDate);
    return publication;
  }
}
