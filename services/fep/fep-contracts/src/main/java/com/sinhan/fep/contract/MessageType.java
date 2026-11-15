package com.sinhan.fep.contract;

/**
 * 전문종별코드. 실제 금융 전문의 4자리 코드 체계를 흉내만 낸다(실제 규격 아님).
 *
 * <p>ONLINE 전문은 건별 거래, BATCH 전문은 일 마감에서만 쓴다. 한국은행은 BATCH 전문만 받는다.
 */
public enum MessageType {
  CARD_APPROVAL("0200", Kind.ONLINE),
  CARD_CANCEL("0420", Kind.ONLINE),
  TRANSFER("0300", Kind.ONLINE),
  WITHDRAW("0310", Kind.ONLINE),
  DEPOSIT("0320", Kind.ONLINE),
  TRANSACTION_INQUIRY("0600", Kind.ONLINE),
  BALANCE_INQUIRY("0610", Kind.ONLINE),
  DAILY_TRANSACTIONS("0800", Kind.BATCH),
  DAY_CLOSE("0900", Kind.BATCH),
  NET_SETTLEMENT("0910", Kind.BATCH);

  public enum Kind {
    ONLINE,
    BATCH
  }

  public final String code;
  public final Kind kind;

  MessageType(String code, Kind kind) {
    this.code = code;
    this.kind = kind;
  }
}
