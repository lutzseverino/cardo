package io.github.lutzseverino.cardo.invite.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cardo.invite.invitation")
public record InvitationProperties(Duration ttl, Duration acceptanceClockSkew) {

  public InvitationProperties {
    if (ttl == null || ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("cardo.invite.invitation.ttl must be positive.");
    }
    if (acceptanceClockSkew == null || acceptanceClockSkew.isNegative()) {
      throw new IllegalArgumentException(
          "cardo.invite.invitation.acceptance-clock-skew must not be negative.");
    }
  }
}
