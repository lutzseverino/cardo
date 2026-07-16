package io.github.lutzseverino.cardo.common.validation;

import io.github.lutzseverino.cardo.common.time.TimeZoneIds;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public final class TimeZoneIdValidator implements ConstraintValidator<TimeZoneId, String> {

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    return value == null || TimeZoneIds.valid(value);
  }
}
