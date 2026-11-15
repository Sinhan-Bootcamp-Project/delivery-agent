package com.sinhan.payment.paymoney.domain;

import java.time.*;

/**
 * 페이머니 충전 1건(F-03). 사용자 은행 계좌 → 결제원 → 한끼 충전금 별도관리 계좌.
 *
 * <pre>REQUESTED → COMPLETED | FAILED | UNKNOWN,  UNKNOWN → COMPLETED | FAILED</pre>
 */
public record TopUp(
    String id,
    String userId,
    String bankAccountNo,
    long amountKrw,
    String status,
    String failureCode,
    LocalDate businessDate,
    Instant updatedAt) {
  public static final String REQUESTED = "REQUESTED",
      COMPLETED = "COMPLETED",
      FAILED = "FAILED",
      UNKNOWN = "UNKNOWN";

  /** 1회 충전 한도와 보유 한도(전자금융거래법 선불전자지급수단 200만원 참고). */
  public static final long MIN_KRW = 1_000, MAX_KRW = 1_000_000, HOLDING_LIMIT_KRW = 2_000_000;

  public boolean unresolved() {
    return status.equals(REQUESTED) || status.equals(UNKNOWN);
  }
}
