package com.sinhan.payment.ledger.domain;

/**
 * 한끼 계정과목(LED-03). 사용자 페이머니만 사용자별 계정이다.
 *
 * <table>
 *   <tr><td>PAYMONEY:{userId}</td><td>부채</td><td>사용자 페이머니(선불충전금). 음수 불가</td></tr>
 *   <tr><td>TRUST_DEPOSIT</td><td>자산</td><td>은행 충전금 별도관리 계좌 예치금</td></tr>
 *   <tr><td>CARD_RECEIVABLE</td><td>자산</td><td>카드사 미수금(승인 후 대금 지급 전)</td></tr>
 *   <tr><td>SETTLEMENT_DEPOSIT</td><td>자산</td><td>은행 정산계좌 예금(카드대금 입금)</td></tr>
 *   <tr><td>MERCHANT_PAYABLE</td><td>부채</td><td>가맹점 미지급금</td></tr>
 *   <tr><td>BENEFIT_EXPENSE</td><td>비용</td><td>즉시할인 등 혜택 비용</td></tr>
 *   <tr><td>OPENING_EQUITY</td><td>자본</td><td>시연 초기 잔액</td></tr>
 * </table>
 */
public final class Accounts {
  public static final String TRUST_DEPOSIT = "TRUST_DEPOSIT";
  public static final String CARD_RECEIVABLE = "CARD_RECEIVABLE";
  public static final String SETTLEMENT_DEPOSIT = "SETTLEMENT_DEPOSIT";
  public static final String MERCHANT_PAYABLE = "MERCHANT_PAYABLE";
  public static final String BENEFIT_EXPENSE = "BENEFIT_EXPENSE";
  public static final String PAYMONEY_PREFIX = "PAYMONEY:";

  private Accounts() {}

  public static String paymoney(String userId) {
    return PAYMONEY_PREFIX + userId;
  }
}
