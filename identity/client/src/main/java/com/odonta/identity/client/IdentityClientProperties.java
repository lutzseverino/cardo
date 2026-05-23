package com.odonta.identity.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "odonta.identity.client")
public record IdentityClientProperties(String baseUrl) {}
