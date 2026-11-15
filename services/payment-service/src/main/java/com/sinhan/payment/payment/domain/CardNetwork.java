package com.sinhan.payment.payment.domain;

/** 카드사 호출 포트(FEP 경유). 결과는 성공 / 거절 / 모름 / 없음 네 가지뿐이다. */
public interface CardNetwork {
  enum Outcome {
    SUCCESS,
    DECLINED,
    NOT_FOUND,
    UNKNOWN
  }

  record Result(Outcome outcome, String code, String approvalNo, String institutionStatus) {}

  Result approve(String executionId, String cardToken, long amountKrw, String merchantName);

  Result cancel(String executionId, long amountKrw);

  /** 같은 거래ID로 카드사 처리 상태를 조회한다. institutionStatus = APPROVED | CANCELLED. */
  Result inquiry(String executionId);

  /** 남은 한도. 조회 실패면 -1. */
  long remainingLimit(String cardToken);
}
