package io.github.lutzseverino.cardo.invite;

import io.github.lutzseverino.cardo.invite.config.InvitationCompletionProperties;
import io.github.lutzseverino.cardo.invite.config.InvitationDeliveryProperties;
import io.github.lutzseverino.cardo.invite.config.InvitationProperties;
import io.github.lutzseverino.cardo.invite.config.KeycloakProperties;
import io.github.lutzseverino.cardo.invite.config.ProductCallerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
  InvitationCompletionProperties.class,
  InvitationDeliveryProperties.class,
  InvitationProperties.class,
  KeycloakProperties.class,
  ProductCallerProperties.class
})
public class InviteApplication {

  public static void main(String[] args) {
    SpringApplication.run(InviteApplication.class, args);
  }
}
