package com.sinhan.payment.settlement.domain;

/**
 * 23:30 일 마감 단계(F-08). 앞 단계가 끝나야 다음 단계를 시작한다. 각 단계는 멱등이라 실패 지점부터 다시 실행할 수 있다.
 */
public enum Step {
  /** 카드사 마감: 당일 승인-취소 금액을 결제원 이체로 한끼 정산계좌에 지급. */
  CARD_CLOSE,
  /** 결제원 마감: 기관별 차액 산출(합계 0) → 한국은행 차액결제. */
  KFTC_CLOSE,
  /** 결제원 차액 ↔ 한국은행 배치 대조. */
  BOK_CHECK,
  /** 한끼 결제 ↔ 카드사 내역 대사, UNKNOWN 확정. */
  RECONCILE_CARD,
  /** 한끼 충전 ↔ 결제원 이체 대사, UNKNOWN 확정. */
  RECONCILE_TOPUP,
  /** 카드대금 입금 확인 → 원장 기장(정산계좌/카드 미수금). */
  CARD_PAYOUT,
  /** 충전금 별도관리: 사용자 페이머니 합 = 은행 충전금 계좌 잔액. */
  TRUST_CHECK,
  /** 원장 검사: 차변 합 = 대변 합, 캐시 잔액 = 분개 합. */
  LEDGER_CHECK
}
