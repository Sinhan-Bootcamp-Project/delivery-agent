package com.sinhan.institution.card.domain;

import java.time.LocalDate;

/** 카드 승인 1건. 한끼 거래ID가 PK라서 같은 ID 재요청은 같은 승인번호를 돌려준다. */
public record Authorization(
    String transactionId,
    String cardToken,
    long amountKrw,
    String approvalNo,
    String status,
    LocalDate businessDate,
    LocalDate cancelBusinessDate) {

  public boolean sameRequest(String token, long amount) {
    return cardToken.equals(token) && amountKrw == amount;
  }
}
