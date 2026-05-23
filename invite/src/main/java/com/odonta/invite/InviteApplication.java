package com.odonta.invite;

import com.odonta.invite.config.InvitationProperties;
import com.odonta.invite.config.KeycloakProperties;
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
