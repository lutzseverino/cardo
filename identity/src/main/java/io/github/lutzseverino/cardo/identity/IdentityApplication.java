package io.github.lutzseverino.cardo.identity;

import io.github.lutzseverino.cardo.openapi.mapping.OpenApiNullableConversions;
import io.github.lutzseverino.cardo.openapi.mapping.UriResponseConversions;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
@Import({OpenApiNullableConversions.class, UriResponseConversions.class})
public class IdentityApplication {

  public static void main(String[] args) {
    SpringApplication.run(IdentityApplication.class, args);
  }
}
