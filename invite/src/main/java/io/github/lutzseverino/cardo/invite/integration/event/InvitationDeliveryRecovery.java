package io.github.lutzseverino.cardo.invite.integration.event;

import java.time.Duration;
import java.time.Instant;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;

class InvitationDeliveryRecovery {

  private final IncompleteEventPublications publications;
  private final Duration retryDelay;

  InvitationDeliveryRecovery(IncompleteEventPublications publications, Duration retryDelay) {
    this.publications = publications;
    this.retryDelay = retryDelay;
  }

  @Scheduled(fixedDelayString = "${cardo.invite.delivery.retry-delay:PT1M}")
  void retryIncomplete() {
    Instant cutoff = Instant.now().minus(retryDelay);
    publications.resubmitIncompletePublications(
        publication ->
            publication.getPublicationDate().isBefore(cutoff)
                && publication.getEvent() instanceof InvitationDeliveryRequested);
  }
}
