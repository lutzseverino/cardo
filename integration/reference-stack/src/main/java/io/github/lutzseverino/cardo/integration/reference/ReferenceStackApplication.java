package io.github.lutzseverino.cardo.integration.reference;

import io.github.lutzseverino.cardo.authorization.grant.AuthorizationPlanConfiguration;
import io.github.lutzseverino.cardo.authorization.schema.AuthorizationSchemaConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@Import({AuthorizationPlanConfiguration.class, AuthorizationSchemaConfiguration.class})
public class ReferenceStackApplication {

  public static void main(String[] args) {
    SpringApplication.run(ReferenceStackApplication.class, args);
  }
}
