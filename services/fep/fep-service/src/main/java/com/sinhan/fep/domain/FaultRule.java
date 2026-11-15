package com.sinhan.fep.domain;

import com.sinhan.fep.contract.*;

/**
 * 기관(+전문종별) 단위 장애 규칙. remaining 횟수만큼 적용된다. messageType이 null이면 그 기관의 모든 전문에 적용.
 *
 * @param rejectCode REJECT일 때 돌려줄 응답코드(기본 1002 한도초과).
 */
public record FaultRule(
    Institution institution, MessageType messageType, FaultMode mode, long delayMs, String rejectCode, int remaining) {
  public FaultRule {
    if (remaining < 0 || remaining > 100) throw new IllegalArgumentException("remaining 0..100");
    if (delayMs < 0 || delayMs > 30_000) throw new IllegalArgumentException("delayMs 0..30000");
    if (rejectCode == null) rejectCode = ResponseCode.LIMIT_EXCEEDED.code;
  }

  public boolean matches(Header h) {
    return institution == h.institution() && (messageType == null || messageType == h.type());
  }
}
