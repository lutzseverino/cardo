package com.odonta.invite.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "odonta.invite.invitation")
public record InvitationProperties(Duration ttl, String webUrl) {}
