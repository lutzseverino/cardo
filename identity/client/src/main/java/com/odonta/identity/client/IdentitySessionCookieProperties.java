package com.odonta.identity.client;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "odonta.identity.session-cookie")
public record IdentitySessionCookieProperties(String name, Duration ttl) {}
