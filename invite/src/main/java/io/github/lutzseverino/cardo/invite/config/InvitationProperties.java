package io.github.lutzseverino.cardo.invite.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cardo.invite.invitation")
public record InvitationProperties(Duration ttl, String webUrl) {}
