package com.odonta.billing;

import com.odonta.billing.config.KeycloakProperties;
import com.odonta.billing.config.StripeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({KeycloakProperties.class, StripeProperties.class})
public class BillingApplication {

  public static void main(String[] args) {
    SpringApplication.run(BillingApplication.class, args);
  }
}
