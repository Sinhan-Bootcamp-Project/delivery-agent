package com.sinhan.institution.card.domain;

import com.sinhan.fep.contract.ResponseCode;

/** 카드 1장. 한도 = limit - used. 정지 카드는 승인 불가. */
public record Card(String token, String maskedNo, long limitKrw, long usedKrw, String status) {
  public long remainingKrw() {
    return limitKrw - usedKrw;
  }

  /** 승인 가능 여부. 가능하면 OK, 아니면 거절 사유. */
  public ResponseCode authorize(long amountKrw) {
    if (amountKrw <= 0) return ResponseCode.INVALID_STATE;
    if (!"ACTIVE".equals(status)) return ResponseCode.SUSPENDED;
    if (amountKrw > remainingKrw()) return ResponseCode.LIMIT_EXCEEDED;
    return ResponseCode.OK;
  }
}
