package io.github.lutzseverino.cardo.invite.integration.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;

class InvitationDeliveryRecoveryTest {

  @Test
  void retriesOnlyInvitationDeliveryPublications() {
    IncompleteEventPublications publications = mock(IncompleteEventPublications.class);

    new InvitationDeliveryRecovery(publications, Duration.ofMinutes(1)).retryIncomplete();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Predicate<EventPublication>> filter = ArgumentCaptor.forClass(Predicate.class);
    verify(publications).resubmitIncompletePublications(filter.capture());
    assertThat(filter.getValue())
        .accepts(
            publication(
                new InvitationDeliveryRequested(UUID.randomUUID()),
                Instant.now().minus(Duration.ofMinutes(2))))
        .rejects(
            publication(new InvitationDeliveryRequested(UUID.randomUUID()), Instant.now()),
            publication(new Object(), Instant.EPOCH));
  }

  private EventPublication publication(Object event, Instant publicationDate) {
    EventPublication publication = mock(EventPublication.class);
    org.mockito.Mockito.when(publication.getEvent()).thenReturn(event);
    org.mockito.Mockito.when(publication.getPublicationDate()).thenReturn(publicationDate);
    return publication;
  }
}
