package com.sinhan.fep.contract;

/**
 * 표준 응답코드. 기관별 사유를 내부 코드로 통일한다.
 *
 * <p>{@link #TIMEOUT}은 기관이 아니라 FEP가 만든다. "실패"가 아니라 "결과를 모른다"는 뜻이므로 호출자는 재전송하지 말고
 * 같은 거래ID로 {@link MessageType#TRANSACTION_INQUIRY}를 보내야 한다.
 */
public enum ResponseCode {
  OK("0000", Outcome.SUCCESS),
  INSUFFICIENT_FUNDS("1001", Outcome.DECLINED),
  LIMIT_EXCEEDED("1002", Outcome.DECLINED),
  SUSPENDED("1003", Outcome.DECLINED),
  NO_ACCOUNT("1004", Outcome.DECLINED),
  INVALID_STATE("1005", Outcome.DECLINED),
  DUPLICATE_CONFLICT("1009", Outcome.DECLINED),
  NOT_FOUND("4040", Outcome.NOT_FOUND),
  NOT_ALLOWED("4050", Outcome.DECLINED),
  TIMEOUT("9100", Outcome.UNKNOWN),
  SYSTEM_ERROR("9999", Outcome.UNKNOWN);

  public enum Outcome {
    SUCCESS,
    DECLINED,
    NOT_FOUND,
    UNKNOWN
  }

  public final String code;
  public final Outcome outcome;

  ResponseCode(String code, Outcome outcome) {
    this.code = code;
    this.outcome = outcome;
  }

  public static ResponseCode of(String code) {
    if (code == null) return SYSTEM_ERROR;
    for (var r : values()) if (r.code.equals(code)) return r;
    return SYSTEM_ERROR;
  }
}
