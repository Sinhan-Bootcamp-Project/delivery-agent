package com.sinhan.fep.contract;

import java.time.LocalDate;
import java.util.List;

/** 전문 바디. 금액은 원 단위 long. 카드번호 원문은 싣지 않고 카드 토큰만 싣는다. */
public final class Bodies {
  private Bodies() {}

  /** 0200 카드 승인 요청 / 응답. */
  public record CardApproval(String cardToken, long amountKrw, String merchantName) {}

  public record CardApprovalResult(String approvalNo, String maskedCardNo) {}

  /** 0420 카드 취소. 원거래 거래ID로 취소한다. */
  public record CardCancel(String originalTransactionId, long amountKrw) {}

  /**
   * 0300 이체 요청(결제원). 출금 기관·계좌 → 입금 기관·계좌.
   *
   * @param purpose TOPUP(페이머니 충전), CARD_PAYOUT(카드 대금 지급) 등. 대사 분류용.
   */
  public record Transfer(
      String fromInstitution,
      String fromAccount,
      String toInstitution,
      String toAccount,
      long amountKrw,
      String purpose) {}

  /** 이체 결과. legs는 구간별 상태(REC-08). */
  public record TransferResult(String status, List<Leg> legs) {}

  public record Leg(String name, String institution, String status) {}

  /** 0310/0320 은행 출금·입금(결제원 → 은행). legId로 구간별 멱등 처리. */
  public record AccountPosting(String legId, String accountNo, long amountKrw, String memo) {}

  /** 0600 거래 상태 조회. 헤더의 거래ID로 조회한다. */
  public record InquiryResult(String status, long amountKrw, String detail) {}

  /** 0610 잔액(카드는 남은 한도) 조회. */
  public record BalanceInquiry(String accountOrToken) {}

  public record BalanceResult(String accountOrToken, long balanceKrw) {}

  /** 0800 일자별 거래내역(대사용). */
  public record DailyQuery(LocalDate businessDate) {}

  public record DailyRecord(String transactionId, String type, long amountKrw, String status) {}

  public record DailyTransactions(LocalDate businessDate, List<DailyRecord> records) {}

  /** 0900 일 마감. 카드사는 카드대금 지급 이체를, 결제원은 차액 산출과 한국은행 전송을 수행한다. */
  public record DayClose(LocalDate businessDate) {}

  public record DayCloseResult(
      LocalDate businessDate, String status, long amountKrw, List<NetEntry> netEntries) {}

  /** 0910 차액결제(결제원 → 한국은행). 합계는 반드시 0. */
  public record NetSettlement(LocalDate businessDate, List<NetEntry> entries) {}

  /** 기관별 차액. 양수 = 받을 돈, 음수 = 줄 돈. */
  public record NetEntry(String institution, long netKrw) {}
}
