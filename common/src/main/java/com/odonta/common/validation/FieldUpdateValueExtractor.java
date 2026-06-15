package com.odonta.common.validation;

import com.odonta.common.model.FieldUpdate;
import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.ValueExtractor;

/** Exposes present partial-update values to Jakarta Bean Validation. */
public final class FieldUpdateValueExtractor
    implements ValueExtractor<FieldUpdate<@ExtractedValue ?>> {

  @Override
  public void extractValues(FieldUpdate<?> update, ValueReceiver receiver) {
    if (update.present()) {
      receiver.value(null, update.value());
    }
  }
}
