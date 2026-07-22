package io.github.lutzseverino.cardo.billing.config;

import com.stripe.StripeClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

  @Bean
  StripeClient stripeClient(StripeProperties properties) {
    return StripeClient.builder()
        .setApiKey(properties.secretKey())
        .setConnectTimeout(Math.toIntExact(properties.connectTimeout().toMillis()))
        .setReadTimeout(Math.toIntExact(properties.readTimeout().toMillis()))
        .setMaxNetworkRetries(0)
        .build();
  }
}
