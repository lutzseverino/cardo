package io.github.lutzseverino.cardo.authorization.grant;

import java.time.Duration;
import java.time.Instant;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;

class AuthorizationPlanRecovery {

  private final IncompleteEventPublications publications;
  private final Duration retryDelay;

  AuthorizationPlanRecovery(IncompleteEventPublications publications, Duration retryDelay) {
    this.publications = publications;
    this.retryDelay = retryDelay;
  }

  @Scheduled(fixedDelayString = "${cardo.authorization.plans.retry-delay:PT1M}")
  void retryIncomplete() {
    Instant cutoff = Instant.now().minus(retryDelay);
    publications.resubmitIncompletePublications(
        publication ->
            publication.getPublicationDate().isBefore(cutoff)
                && (publication.getEvent() instanceof StagedGrantPlan
                    || publication.getEvent() instanceof RevocationPlan));
  }
}
