package com.gaokao.essay.backend.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextUtils {

  private static final Pattern ENGLISH_WORD = Pattern.compile("[A-Za-z]+(?:['-][A-Za-z]+)*");
  private static final DateTimeFormatter ISO_TIME =
      DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("Asia/Shanghai"));

  private TextUtils() {
  }

  public static String trimToEmpty(String value) {
    return value == null ? "" : value.trim();
  }

  public static boolean isBlank(String value) {
    return trimToEmpty(value).isEmpty();
  }

  public static int countEnglishWords(String text) {
    Matcher matcher = ENGLISH_WORD.matcher(text == null ? "" : text);
    int count = 0;
    while (matcher.find()) {
      count++;
    }
    return count;
  }

  public static String summarize(String text, int maxLength) {
    String normalized = trimToEmpty(text).replaceAll("\\s+", " ");
    if (normalized.length() <= maxLength) {
      return normalized;
    }
    return normalized.substring(0, Math.max(maxLength - 1, 1)) + "...";
  }

  public static String uid(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }

  public static String sha256(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder();
      for (byte value : bytes) {
        builder.append(String.format("%02x", value));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 not available", error);
    }
  }

  public static String slugUserId(String seed) {
    return "u_" + sha256(seed).substring(0, 24);
  }

  public static String extractJsonObject(String text) {
    String source = trimToEmpty(text);
    if (source.startsWith("```")) {
      int firstLineEnd = source.indexOf('\n');
      int lastFence = source.lastIndexOf("```");
      if (firstLineEnd > -1 && lastFence > firstLineEnd) {
        source = source.substring(firstLineEnd + 1, lastFence).trim();
      }
    }

    int start = source.indexOf('{');
    int end = source.lastIndexOf('}');
    if (start >= 0 && end > start) {
      return source.substring(start, end + 1);
    }
    return source;
  }

  public static List<String> chunkText(String text, int size) {
    List<String> parts = new ArrayList<>();
    String normalized = text == null ? "" : text;
    if (normalized.isEmpty()) {
      parts.add("");
      return parts;
    }
    for (int index = 0; index < normalized.length(); index += size) {
      parts.add(normalized.substring(index, Math.min(index + size, normalized.length())));
    }
    return parts;
  }

  public static String normalizeForSecurityCheck(String text) {
    return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC).trim();
  }

  public static String formatInstant(Instant instant) {
    return ISO_TIME.format(instant);
  }

  public static String lower(String value) {
    return trimToEmpty(value).toLowerCase(Locale.ROOT);
  }
}
