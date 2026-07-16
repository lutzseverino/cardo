package io.github.lutzseverino.cardo.invite;

import io.github.lutzseverino.cardo.invite.config.InvitationProperties;
import io.github.lutzseverino.cardo.invite.config.KeycloakProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({InvitationProperties.class, KeycloakProperties.class})
public class InviteApplication {

  public static void main(String[] args) {
    SpringApplication.run(InviteApplication.class, args);
  }
}
