package io.github.lutzseverino.cardo.invite.provider;

public interface InvitationSender {

  void send(String email, String acceptUrl);
}
