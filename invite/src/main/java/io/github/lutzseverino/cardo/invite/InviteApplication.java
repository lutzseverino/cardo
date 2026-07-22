package io.github.lutzseverino.cardo.invite;

import io.github.lutzseverino.cardo.invite.config.InvitationCompletionProperties;
import io.github.lutzseverino.cardo.invite.config.InvitationDeliveryProperties;
import io.github.lutzseverino.cardo.invite.config.InvitationProperties;
import io.github.lutzseverino.cardo.invite.config.InviteDatastoreProperties;
import io.github.lutzseverino.cardo.invite.config.InviteRuntimeProperties;
import io.github.lutzseverino.cardo.invite.config.KeycloakProperties;
import io.github.lutzseverino.cardo.invite.config.ProductCallerProperties;
import io.github.lutzseverino.cardo.invite.config.SmtpTimeoutProperties;
import io.github.lutzseverino.cardo.openapi.mapping.UriResponseConversions;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@Import(UriResponseConversions.class)
@EnableConfigurationProperties({
  InvitationCompletionProperties.class,
  InvitationDeliveryProperties.class,
  InvitationProperties.class,
  InviteDatastoreProperties.class,
  InviteRuntimeProperties.class,
  KeycloakProperties.class,
  ProductCallerProperties.class,
  SmtpTimeoutProperties.class
})
public class InviteApplication {

  public static void main(String[] args) {
    SpringApplication.run(InviteApplication.class, args);
  }
}
