package com.sinhan.payment.settlement.domain;

import java.util.*;

/**
 * 한끼 기록과 기관 일자별 내역을 거래ID·유형 단위로 대조한다(REC-03). 순수 함수.
 *
 * <ul>
 *   <li>한끼 UNKNOWN → 기관 상태로 확정(기관에 없으면 실패로 확정)
 *   <li>한끼 FAILED + 기관에 없음 → 일치
 *   <li>한쪽에만 있음 / 금액 불일치 / 상태 불일치 → 불일치 항목
 * </ul>
 */
public final class Reconciler {
  public static final String UNKNOWN = "UNKNOWN", FAILED = "FAILED";

  private Reconciler() {}

  public record Item(String transactionId, String type, long amountKrw, String status) {
    String key() {
      return transactionId + "|" + type;
    }
  }

  public enum Kind {
    MISSING_AT_INSTITUTION,
    MISSING_AT_HANKI,
    AMOUNT_MISMATCH,
    STATUS_MISMATCH,
    /** UNKNOWN을 기관 내역으로 확정해야 하는 건. remote가 null이면 기관에 없음. */
    RESOLVE_UNKNOWN
  }

  public record Finding(Kind kind, String transactionId, String type, Item local, Item remote) {}

  public static List<Finding> compare(List<Item> local, List<Item> remote) {
    Map<String, Item> theirs = new LinkedHashMap<>();
    for (var r : remote) theirs.put(r.key(), r);
    List<Finding> out = new ArrayList<>();
    for (var l : local) {
      var r = theirs.remove(l.key());
      if (l.status().equals(UNKNOWN)) {
        out.add(new Finding(Kind.RESOLVE_UNKNOWN, l.transactionId(), l.type(), l, r));
        continue;
      }
      if (r == null) {
        if (!l.status().equals(FAILED))
          out.add(new Finding(Kind.MISSING_AT_INSTITUTION, l.transactionId(), l.type(), l, null));
        continue;
      }
      if (l.amountKrw() != r.amountKrw())
        out.add(new Finding(Kind.AMOUNT_MISMATCH, l.transactionId(), l.type(), l, r));
      else if (!l.status().equals(r.status()))
        out.add(new Finding(Kind.STATUS_MISMATCH, l.transactionId(), l.type(), l, r));
    }
    for (var r : theirs.values())
      out.add(new Finding(Kind.MISSING_AT_HANKI, r.transactionId(), r.type(), null, r));
    return out;
  }
}
