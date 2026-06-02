package com.odonta.common.time;

import java.time.DateTimeException;
import java.time.ZoneId;

public final class TimeZoneIds {

  private TimeZoneIds() {}

  public static String normalize(String value) {
    return ZoneId.of(value).getId();
  }

  public static boolean valid(String value) {
    try {
      normalize(value);
      return true;
    } catch (DateTimeException exception) {
      return false;
    }
  }
}
