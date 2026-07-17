package io.github.lutzseverino.cardo.invite.client.http;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cardo.invite.client")
record InviteClientProperties(String baseUrl) {}
