package com.sinhan.fep.contract;

import java.util.*;
import java.util.regex.Pattern;

/** 전문 로그 마스킹(N-02, FEP-05). 카드번호처럼 보이는 숫자열과 민감 필드를 가린다. */
public final class Masking {
  private static final Pattern CARD = Pattern.compile("\\b(\\d{4})[- ]?(\\d{4})[- ]?(\\d{4})[- ]?(\\d{4})\\b");
  private static final Set<String> SENSITIVE = Set.of("cardNo", "cardNumber", "pin", "cvc");

  private Masking() {}

  public static String cardNo(String raw) {
    if (raw == null) return null;
    var digits = raw.replaceAll("\\D", "");
    if (digits.length() < 8) return "****";
    return digits.substring(0, 4) + "-****-****-" + digits.substring(digits.length() - 4);
  }

  public static String text(String raw) {
    return raw == null ? null : CARD.matcher(raw).replaceAll("$1-****-****-$4");
  }

  public static Map<String, Object> body(Map<String, Object> body) {
    var copy = new LinkedHashMap<String, Object>();
    body.forEach(
        (k, v) -> {
          if (SENSITIVE.contains(k)) copy.put(k, "****");
          else if (v instanceof String s) copy.put(k, text(s));
          else copy.put(k, v);
        });
    return copy;
  }
}
