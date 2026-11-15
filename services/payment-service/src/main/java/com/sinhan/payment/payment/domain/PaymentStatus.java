package com.sinhan.payment.payment.domain;

import java.util.*;

/**
 * 결제 상태.
 *
 * <pre>
 * PENDING ─┬→ APPROVED ─→ CANCEL_PENDING ─→ CANCELLED
 *          ├→ FAILED            └────────→ APPROVED (취소 거절)
 *          └→ UNKNOWN ─→ APPROVED | FAILED  (상태 조회 또는 대사로만 확정)
 * </pre>
 *
 * 외부(주문 서비스) 계약에서는 APPROVED를 PAID, CANCELLED를 REFUNDED로 보여준다.
 */
public enum PaymentStatus {
  PENDING,
  APPROVED,
  FAILED,
  UNKNOWN,
  CANCEL_PENDING,
  CANCELLED;

  private static final Map<PaymentStatus, Set<PaymentStatus>> NEXT =
      Map.of(
          PENDING, EnumSet.of(APPROVED, FAILED, UNKNOWN),
          UNKNOWN, EnumSet.of(APPROVED, FAILED),
          APPROVED, EnumSet.of(CANCEL_PENDING, CANCELLED),
          CANCEL_PENDING, EnumSet.of(CANCELLED, APPROVED),
          FAILED, EnumSet.noneOf(PaymentStatus.class),
          CANCELLED, EnumSet.noneOf(PaymentStatus.class));

  public boolean canMoveTo(PaymentStatus next) {
    return NEXT.get(this).contains(next);
  }

  public boolean unresolved() {
    return this == PENDING || this == UNKNOWN;
  }

  public String external() {
    return switch (this) {
      case APPROVED -> "PAID";
      case CANCELLED -> "REFUNDED";
      default -> name();
    };
  }
}
