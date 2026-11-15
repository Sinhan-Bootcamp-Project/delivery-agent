package com.sinhan.payment.payment.domain;

import java.time.*;

/**
 * 결제 1건(애그리거트 루트). ID = 주문 서비스의 executionId(멱등키). digest = 승인된 주문안 스냅샷 해시.
 *
 * @param requestedMethod 혜택 서비스가 정한 수단 코드(CARD_A/CARD_B/LOCAL). 응답에 그대로 돌려준다.
 */
public record Payment(
    String executionId,
    String userId,
    String requestedMethod,
    PaymentMethod method,
    long amountKrw,
    long discountKrw,
    String digest,
    PaymentStatus status,
    String approvalNo,
    String failureCode,
    LocalDate businessDate,
    LocalDate cancelBusinessDate,
    Instant updatedAt) {

  public Payment moveTo(PaymentStatus next) {
    if (!status.canMoveTo(next))
      throw new IllegalStateException("payment " + executionId + " " + status + " -> " + next);
    return new Payment(
        executionId, userId, requestedMethod, method, amountKrw, discountKrw, digest, next, approvalNo,
        failureCode, businessDate, cancelBusinessDate, updatedAt);
  }
}
