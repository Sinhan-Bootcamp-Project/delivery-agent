package com.sinhan.payment.payment.domain;

/**
 * 결제 수단. 혜택 서비스의 수단 코드(CARD_A, CARD_B, LOCAL)를 받아 실제 결제 경로로 바꾼다.
 *
 * <p>LOCAL(지역화폐)은 이번 범위에서 자체 페이머니로 처리한다(잠정 결정, PR 참고).
 */
public enum PaymentMethod {
  CARD_A(true),
  CARD_B(true),
  PAYMONEY(false);

  public final boolean card;

  PaymentMethod(boolean card) {
    this.card = card;
  }

  public static PaymentMethod from(String code) {
    return switch (code) {
      case "CARD_A" -> CARD_A;
      case "CARD_B" -> CARD_B;
      case "LOCAL", "PAYMONEY" -> PAYMONEY;
      default -> throw new IllegalArgumentException("unsupported payment method " + code);
    };
  }
}
