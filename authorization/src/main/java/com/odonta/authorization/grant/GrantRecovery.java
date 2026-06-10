package com.odonta.authorization.grant;

import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;

class GrantRecovery {

  private final FailedEventPublications publications;

  GrantRecovery(FailedEventPublications publications) {
    this.publications = publications;
  }

  @Scheduled(fixedDelayString = "${odonta.authorization.grants.retry-delay:PT1M}")
  void retryFailed() {
    publications.resubmit(ResubmissionOptions.defaults());
  }
}
