package com.sinhan.institution.kftc.domain;

import com.sinhan.fep.contract.Bodies.*;
import java.time.LocalDate;
import java.util.List;

/**
 * 결제원 이체 1건과 구간별 상태(REC-08).
 *
 * <pre>
 * REQUESTED ─출금 OK→ DEBITED ─입금 OK→ COMPLETED
 *     │ 출금 거절            │ 입금 실패
 *     ▼                     ▼
 *   FAILED            보상(역이체) OK → COMPENSATED
 * </pre>
 */
public record TransferOrder(
    String transactionId,
    Transfer request,
    String status,
    String debitStatus,
    String creditStatus,
    String compensationStatus,
    String failureCode,
    LocalDate businessDate) {

  public static final String REQUESTED = "REQUESTED",
      DEBITED = "DEBITED",
      COMPLETED = "COMPLETED",
      FAILED = "FAILED",
      COMPENSATED = "COMPENSATED";

  public boolean terminal() {
    return status.equals(COMPLETED) || status.equals(FAILED) || status.equals(COMPENSATED);
  }

  public String debitLeg() {
    return transactionId + ":D";
  }

  public String creditLeg() {
    return transactionId + ":C";
  }

  public String compensationLeg() {
    return transactionId + ":R";
  }

  public TransferResult result() {
    return new TransferResult(
        status,
        List.of(
            new Leg("DEBIT", request.fromInstitution(), debitStatus),
            new Leg("CREDIT", request.toInstitution(), creditStatus),
            new Leg("COMPENSATION", request.fromInstitution(), compensationStatus)));
  }
}
