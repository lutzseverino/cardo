package io.github.lutzseverino.cardo.invite.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.lutzseverino.cardo.common.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class InviteExceptionHandlerTest {

  @Test
  void returnsTheDocumentedErrorEnvelopeForApiExceptions() throws Exception {
    try (AnnotationConfigWebApplicationContext context =
        new AnnotationConfigWebApplicationContext()) {
      context.setServletContext(new MockServletContext());
      context.register(WebTestConfiguration.class);
      context.refresh();

      MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();

      mvc.perform(get("/invitation"))
          .andExpect(status().isGone())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.error.code").value("invitation_expired"))
          .andExpect(jsonPath("$.error.message").value("Invitation expired."))
          .andExpect(jsonPath("$.error.details").isEmpty());
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableWebMvc
  @ComponentScan(
      basePackages = "io.github.lutzseverino.cardo.invite.exception",
      useDefaultFilters = false,
      includeFilters =
          @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = RestControllerAdvice.class))
  static class WebTestConfiguration {

    @Bean
    FailingController failingController() {
      return new FailingController();
    }
  }

  @RestController
  static class FailingController {

    @GetMapping("/invitation")
    void getInvitation() {
      throw ApiException.gone("invitation_expired", "Invitation expired.");
    }
  }
}
