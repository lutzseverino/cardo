package io.github.lutzseverino.cardo.authorization.grant;

import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;

class AuthorizationPlanRecovery {

  private final FailedEventPublications publications;

  AuthorizationPlanRecovery(FailedEventPublications publications) {
    this.publications = publications;
  }

  @Scheduled(fixedDelayString = "${cardo.authorization.plans.retry-delay:PT1M}")
  void retryFailed() {
    publications.resubmit(
        ResubmissionOptions.defaults()
            .withFilter(
                publication ->
                    publication.getEvent() instanceof GrantPlan
                        || publication.getEvent() instanceof RevocationPlan));
  }
}
