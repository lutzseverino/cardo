package com.odonta.common.web;

import java.time.Duration;
import org.springframework.http.ResponseCookie;

public final class SessionCookies {

  private SessionCookies() {}

  public static ResponseCookie create(String name, String token, Duration ttl) {
    return base(name, token).maxAge(ttl).build();
  }

  public static ResponseCookie expire(String name) {
    return base(name, "").maxAge(0).build();
  }

  private static ResponseCookie.ResponseCookieBuilder base(String name, String value) {
    return ResponseCookie.from(name, value).httpOnly(true).sameSite("Lax").path("/");
  }
}
