package com.odonta.identity.client.http;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "odonta.identity.client")
record IdentityClientProperties(String baseUrl) {}
