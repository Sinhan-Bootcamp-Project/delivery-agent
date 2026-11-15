package com.sinhan.payment.paymoney.domain;

/** 결제원 이체 포트(FEP 경유). */
public interface TransferNetwork {
  enum Outcome {
    COMPLETED,
    FAILED,
    NOT_FOUND,
    UNKNOWN
  }

  record Result(Outcome outcome, String code) {}

  Result topUp(String id, String fromAccount, long amountKrw);

  Result inquiry(String id);

  /** 은행 충전금 별도관리 계좌 잔액. 조회 실패면 empty. */
  java.util.OptionalLong trustBalance();
}
