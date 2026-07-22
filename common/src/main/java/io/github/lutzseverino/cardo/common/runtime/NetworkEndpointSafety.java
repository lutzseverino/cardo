package io.github.lutzseverino.cardo.common.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
    Long ipv4 = parseIpv4Literal(normalized);
    if (ipv4 != null) {
      return isLocalOrUnspecifiedIpv4(ipv4);
    }
    if (looksLikeIpv4Literal(normalized)) {
      return true;
    }
    if (!normalized.contains(":")) {
      return false;
    }
    int[] ipv6 = parseIpv6Literal(normalized);
    if (ipv6 == null) {
      return true;
    }
    if (Arrays.stream(ipv6).allMatch(word -> word == 0)) {
      return true;
    }
    boolean loopback = Arrays.stream(ipv6, 0, 7).allMatch(word -> word == 0) && ipv6[7] == 1;
    if (loopback) {
      return true;
    }
    boolean ipv4Compatible = Arrays.stream(ipv6, 0, 6).allMatch(word -> word == 0);
    boolean ipv4Mapped = Arrays.stream(ipv6, 0, 5).allMatch(word -> word == 0) && ipv6[5] == 0xffff;
    if (ipv4Compatible || ipv4Mapped) {
      long embeddedIpv4 = ((long) ipv6[6] << 16) | ipv6[7];
      return isLocalOrUnspecifiedIpv4(embeddedIpv4);
    }
    return false;
  }

  private static Long parseIpv4Literal(String host) {
    String[] parts = host.split("\\.", -1);
    if (parts.length < 1 || parts.length > 4) {
      return null;
    }
    long[] values = new long[parts.length];
    for (int index = 0; index < parts.length; index++) {
      String part = parts[index];
      if (part.isEmpty() || !part.chars().allMatch(Character::isDigit)) {
        return null;
      }
      try {
        values[index] = Long.parseLong(part);
      } catch (NumberFormatException exception) {
        return null;
      }
    }
    long address;
    switch (values.length) {
      case 1 -> address = bounded(values[0], 0xffff_ffffL);
      case 2 -> address = (bounded(values[0], 0xff) << 24) | bounded(values[1], 0xff_ffff);
      case 3 ->
          address =
              (bounded(values[0], 0xff) << 24)
                  | (bounded(values[1], 0xff) << 16)
                  | bounded(values[2], 0xffff);
      case 4 ->
          address =
              (bounded(values[0], 0xff) << 24)
                  | (bounded(values[1], 0xff) << 16)
                  | (bounded(values[2], 0xff) << 8)
                  | bounded(values[3], 0xff);
      default -> throw new IllegalStateException("Unexpected IPv4 part count.");
    }
    return address < 0 ? null : address;
  }

  private static long bounded(long value, long maximum) {
    return value <= maximum ? value : -1;
  }

  private static boolean looksLikeIpv4Literal(String host) {
    return !host.isEmpty()
        && host.chars().anyMatch(Character::isDigit)
        && host.chars().allMatch(character -> Character.isDigit(character) || character == '.');
  }

  private static boolean isLocalOrUnspecifiedIpv4(long address) {
    return address == 0 || (address >>> 24) == 127;
  }

  private static int[] parseIpv6Literal(String host) {
    int scopeSeparator = host.indexOf('%');
    if (scopeSeparator >= 0) {
      if (scopeSeparator == 0 || scopeSeparator == host.length() - 1) {
        return null;
      }
      host = host.substring(0, scopeSeparator);
    }
    int compression = host.indexOf("::");
    if (compression >= 0 && host.indexOf("::", compression + 2) >= 0) {
      return null;
    }
    if (compression < 0) {
      List<Integer> words = parseIpv6Section(host, true);
      return words != null && words.size() == 8 ? toArray(words) : null;
    }
    String leftValue = host.substring(0, compression);
    String rightValue = host.substring(compression + 2);
    List<Integer> left = parseIpv6Section(leftValue, rightValue.isEmpty());
    List<Integer> right = parseIpv6Section(rightValue, true);
    if (left == null || right == null || left.size() + right.size() >= 8) {
      return null;
    }
    List<Integer> words = new ArrayList<>(8);
    words.addAll(left);
    while (words.size() < 8 - right.size()) {
      words.add(0);
    }
    words.addAll(right);
    return toArray(words);
  }

  private static List<Integer> parseIpv6Section(String section, boolean allowEmbeddedIpv4) {
    if (section.isEmpty()) {
      return new ArrayList<>();
    }
    String[] tokens = section.split(":", -1);
    List<Integer> words = new ArrayList<>(tokens.length);
    for (int index = 0; index < tokens.length; index++) {
      String token = tokens[index];
      if (token.isEmpty()) {
        return null;
      }
      if (token.contains(".")) {
        if (!allowEmbeddedIpv4 || index != tokens.length - 1) {
          return null;
        }
        Long ipv4 = parseIpv4Literal(token);
        if (ipv4 == null) {
          return null;
        }
        words.add((int) (ipv4 >>> 16));
        words.add((int) (ipv4 & 0xffff));
      } else {
        if (token.length() > 4
            || !token.chars().allMatch(character -> Character.digit(character, 16) >= 0)) {
          return null;
        }
        try {
          words.add(Integer.parseInt(token, 16));
        } catch (NumberFormatException exception) {
          return null;
        }
      }
    }
    return words;
  }

  private static int[] toArray(List<Integer> words) {
    int[] result = new int[words.size()];
    for (int index = 0; index < words.size(); index++) {
      result[index] = words.get(index);
    }
    return result;
  }

  private static String normalize(String host) {
    String normalized = host.strip().toLowerCase(Locale.ROOT);
    if (normalized.startsWith("[") && normalized.endsWith("]")) {
      normalized = normalized.substring(1, normalized.length() - 1);
    }
    return normalized.replaceFirst("\\.+$", "");
  }
}
