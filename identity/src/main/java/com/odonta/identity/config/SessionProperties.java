package com.odonta.identity.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "odonta.identity.session")
public record SessionProperties(String cookieName, Duration ttl) {}
