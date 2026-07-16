package io.github.lutzseverino.cardo.identity.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cardo.identity.session")
public record SessionProperties(String cookieName, Duration ttl) {}
