package com.odonta.openapi.mapping;

import java.net.URI;
import org.mapstruct.Named;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.stereotype.Component;

/** MapStruct conversion for application-owned URI strings and generated URI fields. */
@Component
public class UriResponseConversions {

  public URI toUri(String value) {
    return value == null ? null : URI.create(value);
  }

  @Named("toNullableUri")
  public JsonNullable<URI> toNullableUri(String value) {
    return JsonNullable.of(toUri(value));
  }
}
