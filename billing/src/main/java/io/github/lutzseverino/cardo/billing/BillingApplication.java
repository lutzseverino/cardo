package io.github.lutzseverino.cardo.billing;

import io.github.lutzseverino.cardo.billing.config.CustomerProvisioningProperties;
import io.github.lutzseverino.cardo.billing.config.KeycloakProperties;
import io.github.lutzseverino.cardo.billing.config.StripeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
  KeycloakProperties.class,
  StripeProperties.class,
  CustomerProvisioningProperties.class
})
public class BillingApplication {

  public static void main(String[] args) {
    SpringApplication.run(BillingApplication.class, args);
  }
}
