package io.github.lutzseverino.cardo.common.runtime;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

/** Pure, DNS-free classification of hosts that must not be used as production dependencies. */
public final class NetworkEndpointSafety {

  private NetworkEndpointSafety() {}

  public static boolean isLocalOrUnspecified(String host) {
    if (host == null || host.isBlank()) {
      return true;
    }
    String normalized = normalize(host);
    if (normalized.equals("localhost") || normalized.endsWith(".localhost")) {
      return true;
    }
    if (isIpv4Literal(normalized)) {
      String[] octets = normalized.split("\\.");
      return Integer.parseInt(octets[0]) == 127 || normalized.equals("0.0.0.0");
    }
    if (!normalized.isEmpty() && normalized.chars().allMatch(Character::isDigit)) {
      return classifyIpLiteral(normalized);
    }
    if (!normalized.contains(":")) {
      return false;
    }
    return classifyIpLiteral(normalized);
  }

  private static boolean classifyIpLiteral(String host) {
    try {
      InetAddress address = InetAddress.getByName(host);
      return address.isLoopbackAddress() || address.isAnyLocalAddress();
    } catch (UnknownHostException exception) {
      return true;
    }
  }

  private static String normalize(String host) {
    String normalized = host.strip().toLowerCase(Locale.ROOT);
    if (normalized.startsWith("[") && normalized.endsWith("]")) {
      normalized = normalized.substring(1, normalized.length() - 1);
    }
    return normalized.replaceFirst("\\.+$", "");
  }

  private static boolean isIpv4Literal(String host) {
    String[] octets = host.split("\\.", -1);
    if (octets.length != 4) {
      return false;
    }
    for (String octet : octets) {
      if (octet.isEmpty() || !octet.chars().allMatch(Character::isDigit)) {
        return false;
      }
      try {
        if (Integer.parseInt(octet) > 255) {
          return false;
        }
      } catch (NumberFormatException exception) {
        return false;
      }
    }
    return true;
  }
}
