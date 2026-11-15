package com.sinhan.institution.bank.domain;

import com.sinhan.fep.contract.ResponseCode;

/**
 * 은행 계좌. kind: CUSTOMER(사용자), TRUST(한끼 충전금 별도관리), SETTLEMENT(한끼 정산), CLEARING(타기관 결제 중계).
 */
public record Account(String accountNo, String owner, String kind, long balanceKrw) {
  public ResponseCode canWithdraw(long amountKrw) {
    if (amountKrw <= 0) return ResponseCode.INVALID_STATE;
    return balanceKrw >= amountKrw ? ResponseCode.OK : ResponseCode.INSUFFICIENT_FUNDS;
  }
}
