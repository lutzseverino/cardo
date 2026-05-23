package com.odonta.common.model;

import java.util.Locale;

public record EmailAddress(String value) {

  public EmailAddress {
    value = value.trim().toLowerCase(Locale.ROOT);
  }

  public static EmailAddress of(String value) {
    return new EmailAddress(value);
  }
}
