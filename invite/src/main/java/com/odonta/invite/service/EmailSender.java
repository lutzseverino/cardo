package com.odonta.invite.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EmailSender {

  private static final Logger logger = LoggerFactory.getLogger(EmailSender.class);

  public void sendInvitation(String email, String acceptUrl) {
    logger.info("Invitation email queued for {}", email);
  }
}
