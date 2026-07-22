package io.github.lutzseverino.cardo.invite.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
    info =
        @Info(
            title = "Invite API",
            version = "0.1.0",
            description =
                "Invite owns cross-product invitation tokens, expiry, delivery, provisional identity completion, lifecycle state, and generic access-grant staging. Product services own the domain decision to create or accept an invitation.",
            license = @License(name = "MIT", url = "https://opensource.org/license/mit")),
    servers = @Server(url = "/api/v1"))
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT")
public class OpenApiConfiguration {}
