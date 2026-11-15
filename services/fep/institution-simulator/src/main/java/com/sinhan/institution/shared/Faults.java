package com.sinhan.institution.shared;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * 기관 내부 장애 주입(시험 전용, ENABLE_FAULTS=true). 지연·무응답·응답 유실은 FEP가 주입하고, 여기서는 기관 내부 구간 실패만
 * 다룬다. 예: bank_deposit_fail = 결제원→은행 입금 구간 실패 → 결제원이 보상(역이체)해야 하는 상황.
 */
@Component
public class Faults {
  public static final Set<String> NAMES = Set.of("bank_deposit_fail", "bok_unavailable");
  private final Map<String, AtomicInteger> remaining = new ConcurrentHashMap<>();

  public boolean enabled() {
    return "true".equals(System.getenv("ENABLE_FAULTS"));
  }

  public void set(String name, int count) {
    if (!NAMES.contains(name) || count < 0 || count > 100)
      throw new IllegalArgumentException("invalid fault");
    remaining.put(name, new AtomicInteger(count));
  }

  public boolean take(String name) {
    var r = remaining.get(name);
    return r != null && r.getAndUpdate(v -> Math.max(0, v - 1)) > 0;
  }
}
