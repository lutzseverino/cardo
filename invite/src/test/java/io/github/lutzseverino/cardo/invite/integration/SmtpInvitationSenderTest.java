package io.github.lutzseverino.cardo.invite.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.github.lutzseverino.cardo.invite.config.InvitationDeliveryProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpInvitationSenderTest {

  @Test
  void sendsTheInvitationLinkWithoutLoggingPersonalData() {
    JavaMailSender mail = mock(JavaMailSender.class);
    SmtpInvitationSender sender =
        new SmtpInvitationSender(new InvitationDeliveryProperties("invites@example.com"), mail);

    sender.send("member@example.com", "https://app.example.com/invitations/token-1");

    ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mail).send(message.capture());
    assertThat(message.getValue().getFrom()).isEqualTo("invites@example.com");
    assertThat(message.getValue().getTo()).containsExactly("member@example.com");
    assertThat(message.getValue().getText())
        .contains("https://app.example.com/invitations/token-1");
  }
}
