package io.github.lutzseverino.cardo.invite.integration;

import io.github.lutzseverino.cardo.invite.config.InvitationDeliveryProperties;
import io.github.lutzseverino.cardo.invite.provider.InvitationSender;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SmtpInvitationSender implements InvitationSender {

  private final InvitationDeliveryProperties properties;
  private final JavaMailSender mail;

  @Override
  public void send(String email, String acceptUrl) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(properties.from());
    message.setTo(email);
    message.setSubject("You have been invited");
    message.setText("Open this invitation to continue: " + acceptUrl);
    mail.send(message);
  }
}
