package io.github.lutzseverino.cardo.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = NullOrNotBlankValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface NullOrNotBlank {

  String message() default "must be null or contain non-whitespace text";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
