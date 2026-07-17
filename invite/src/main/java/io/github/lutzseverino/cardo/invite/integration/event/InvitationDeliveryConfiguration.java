package io.github.lutzseverino.cardo.invite.integration.event;

import io.github.lutzseverino.cardo.invite.provider.InvitationDelivery;
import io.github.lutzseverino.cardo.invite.provider.InvitationSender;
import io.github.lutzseverino.cardo.invite.repository.InvitationRepository;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@Configuration(proxyBeanMethods = false)
class InvitationDeliveryConfiguration {

  @Bean
  InvitationDelivery invitationDelivery(ApplicationEventPublisher events) {
    return new DurableInvitationDelivery(events);
  }

  @Bean
  InvitationDeliveryProcessor invitationDeliveryProcessor(
      InvitationSender sender, InvitationRepository invitations) {
    return new InvitationDeliveryProcessor(sender, invitations);
  }

  @Bean
  InvitationDeliveryListener invitationDeliveryListener(InvitationDeliveryProcessor processor) {
    return new InvitationDeliveryListener(processor);
  }

  @Bean
  InvitationDeliveryRecovery invitationDeliveryRecovery(
      IncompleteEventPublications publications,
      @Value("${cardo.invite.delivery.retry-delay:PT1M}") Duration retryDelay) {
    return new InvitationDeliveryRecovery(publications, retryDelay);
  }
}
