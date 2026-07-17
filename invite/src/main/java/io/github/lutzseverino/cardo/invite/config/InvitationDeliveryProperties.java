package io.github.lutzseverino.cardo.invite.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "cardo.invite.delivery")
public record InvitationDeliveryProperties(@NotBlank String from) {}
