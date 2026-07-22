package io.github.lutzseverino.cardo.invite.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProductCallerBindingProperties.class)
class ProductCallerConfiguration {

  @Bean
  ProductCallerProperties productCallerProperties(ProductCallerBindingProperties binding) {
    return binding.toPublicProperties();
  }
}
