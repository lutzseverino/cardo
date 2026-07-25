package io.github.lutzseverino.cardo.identity.workflow;

import io.github.lutzseverino.cardo.identity.service.BrowserSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PurgeBrowserSessionsWorkflow {

  private final BrowserSessionService sessions;

  @Scheduled(fixedDelayString = "${cardo.identity.browser-session.cleanup-delay:PT1H}")
  public void purge() {
    sessions.purgeRetained();
  }
}
