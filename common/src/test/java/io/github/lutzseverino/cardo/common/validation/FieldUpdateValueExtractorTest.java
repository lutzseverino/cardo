package io.github.lutzseverino.cardo.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lutzseverino.cardo.common.model.FieldUpdate;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

class FieldUpdateValueExtractorTest {

  @Test
  void discoversTheExtractorAndValidatesOnlyPresentContainerElements() {
    try (var factory =
        Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory()) {
      Validator validator = factory.getValidator();

      assertThat(validator.validate(new PatchInput(FieldUpdate.absent()))).isEmpty();
      assertThat(validator.validate(new PatchInput(FieldUpdate.present(null))))
          .singleElement()
          .satisfies(violation -> assertThat(violation.getPropertyPath()).hasToString("name"));
      assertThat(validator.validate(new PatchInput(FieldUpdate.present(" ")))).hasSize(1);
      assertThat(validator.validate(new PatchInput(FieldUpdate.present("Employee")))).isEmpty();
    }
  }

  private record PatchInput(FieldUpdate<@NotBlank String> name) {}
}
