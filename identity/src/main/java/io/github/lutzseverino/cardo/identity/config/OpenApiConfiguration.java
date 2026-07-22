package io.github.lutzseverino.cardo.identity.config;

import io.github.lutzseverino.cardo.identity.api.model.AuthenticatedPrincipalResponse;
import io.github.lutzseverino.cardo.identity.api.model.IdentityOperationResponse;
import io.github.lutzseverino.cardo.identity.api.model.UpdateCurrentUserRequest;
import io.github.lutzseverino.cardo.identity.api.model.UpdateUserRequest;
import io.github.lutzseverino.cardo.identity.api.model.UserResponse;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.openapitools.jackson.nullable.JsonNullableJackson3Module;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JacksonModule;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
    info =
        @Info(
            title = "Identity API",
            version = "0.1.0",
            description =
                "Identity owns users, authentication, and the authenticated principal exposed to products. Future account-recovery and external-provider flows are tracked outside this active contract until their Keycloak-backed shape is designed.",
            license = @License(name = "MIT", url = "https://opensource.org/license/mit")),
    servers = @Server(url = "/api/v1"))
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT")
@SecurityScheme(
    name = "identityAccessCookie",
    type = SecuritySchemeType.APIKEY,
    in = SecuritySchemeIn.COOKIE,
    paramName = "__Host-cardo.session")
@SecurityScheme(
    name = "csrfCookie",
    type = SecuritySchemeType.APIKEY,
    in = SecuritySchemeIn.COOKIE,
    paramName = "__Host-cardo.csrf")
@SecurityScheme(
    name = "csrfHeader",
    type = SecuritySchemeType.APIKEY,
    in = SecuritySchemeIn.HEADER,
    paramName = "X-CSRF-TOKEN")
public class OpenApiConfiguration {

  @Bean
  JacksonModule jsonNullableRuntimeSupport() {
    return new JsonNullableJackson3Module();
  }

  @Bean
  ObjectMapperProvider springdocObjectMapperProvider(SpringDocConfigProperties properties) {
    ObjectMapperProvider provider = new ObjectMapperProvider(properties);
    provider.jsonMapper().registerModule(new JsonNullableModule());
    provider.yamlMapper().registerModule(new JsonNullableModule());
    return provider;
  }

  @Bean
  OpenApiCustomizer jsonNullableProperties() {
    return openApi -> {
      List.of(
              AuthenticatedPrincipalResponse.class,
              IdentityOperationResponse.class,
              UpdateCurrentUserRequest.class,
              UpdateUserRequest.class,
              UserResponse.class)
          .forEach(
              model ->
                  Arrays.stream(model.getDeclaredFields())
                      .filter(
                          field ->
                              field
                                  .getType()
                                  .equals(org.openapitools.jackson.nullable.JsonNullable.class))
                      .forEach(
                          field ->
                              ((Schema<?>)
                                      openApi
                                          .getComponents()
                                          .getSchemas()
                                          .get(model.getSimpleName())
                                          .getProperties()
                                          .get(field.getName()))
                                  .setNullable(true)));

      // The generated server models use @Nullable for optional, non-nullable patch fields
      // because Java has no distinct "absent" value. Keep the runtime contract aligned with
      // the source document: only JsonNullable-backed fields accept an explicit JSON null.
      setNullable(openApi, UpdateCurrentUserRequest.class, "name", false);
      setNullable(openApi, UpdateUserRequest.class, "name", false);
      setNullable(openApi, UpdateUserRequest.class, "status", false);
    };
  }

  @Bean
  OpenApiCustomizer browserCsrfRequirement() {
    return openApi ->
        openApi.getPaths().values().stream()
            .flatMap(path -> path.readOperations().stream())
            .filter(operation -> isSplitCsrfRequirement(operation.getSecurity()))
            .forEach(
                operation ->
                    operation.setSecurity(
                        List.of(
                            new SecurityRequirement()
                                .addList("csrfCookie")
                                .addList("csrfHeader"))));
  }

  private static boolean isSplitCsrfRequirement(List<SecurityRequirement> security) {
    return security != null
        && security.size() == 2
        && security.stream().allMatch(requirement -> requirement.size() == 1)
        && security.stream()
            .flatMap(requirement -> requirement.keySet().stream())
            .collect(Collectors.toSet())
            .equals(Set.of("csrfCookie", "csrfHeader"));
  }

  private static void setNullable(
      io.swagger.v3.oas.models.OpenAPI openApi, Class<?> model, String property, boolean nullable) {
    ((Schema<?>)
            openApi
                .getComponents()
                .getSchemas()
                .get(model.getSimpleName())
                .getProperties()
                .get(property))
        .setNullable(nullable);
  }
}
