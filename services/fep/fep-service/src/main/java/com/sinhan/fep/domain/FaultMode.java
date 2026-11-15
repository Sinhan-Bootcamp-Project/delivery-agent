package com.sinhan.fep.domain;

/** 장애 주입 방식(F-10). */
public enum FaultMode {
  /** 기관에 보내기 전에 지연. 지연이 타임아웃 이상이면 기관은 처리하지만 호출자는 TIMEOUT을 받는다. */
  DELAY,
  /** 기관에 보내지 않고 TIMEOUT. 기관에는 거래가 없다. */
  NO_RESPONSE,
  /** 기관은 처리했지만 응답이 유실되어 TIMEOUT. 상태 조회로 확정해야 한다. */
  RESPONSE_LOST,
  /** 기관에 보내지 않고 거절 응답. */
  REJECT
}
