package io.github.lutzseverino.cardo.identity.client.http;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cardo.identity.client")
record IdentityClientProperties(String baseUrl) {}
