package com.sinhan.fep.contract;

import java.time.Instant;

/**
 * 전문 공통부. 모든 기관 전문이 같은 헤더를 쓴다.
 *
 * @param transactionId 한끼 거래ID. 재시도·조회에도 바뀌지 않으며 기관은 이 값으로 멱등 처리한다.
 * @param responseCode 요청에서는 null, 응답에서는 {@link ResponseCode#code}.
 */
public record Header(
    String messageType,
    String transactionId,
    String institutionCode,
    Instant sentAt,
    String responseCode) {

  public static Header request(MessageType type, String transactionId, Institution to) {
    return new Header(type.name(), transactionId, to.code, Instant.now(), null);
  }

  public Header respond(ResponseCode code) {
    return new Header(messageType, transactionId, institutionCode, Instant.now(), code.code);
  }

  public MessageType type() {
    return MessageType.valueOf(messageType);
  }

  public Institution institution() {
    return Institution.of(institutionCode);
  }

  public ResponseCode response() {
    return ResponseCode.of(responseCode);
  }
}
