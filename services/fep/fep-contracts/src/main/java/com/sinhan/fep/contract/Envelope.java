package com.sinhan.fep.contract;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Map;

/**
 * 전문 = 공통 헤더 + 기관별 바디. 바디는 전송 시 Map(JSON 객체)이고 {@link Bodies}의 record로 읽고 쓴다.
 *
 * <p>JSON 전문이다. 고정길이 전문(F-12)은 FEP의 변환 계층에서 추가한다.
 */
public record Envelope(Header header, Map<String, Object> body) {
  static final ObjectMapper MAPPER =
      JsonMapper.builder()
          .addModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .build();

  @SuppressWarnings("unchecked")
  public static Envelope of(Header header, Object body) {
    return new Envelope(header, body == null ? Map.of() : MAPPER.convertValue(body, Map.class));
  }

  public <T> T read(Class<T> type) {
    return MAPPER.convertValue(body == null ? Map.of() : body, type);
  }

  public Envelope reply(ResponseCode code, Object responseBody) {
    return of(header.respond(code), responseBody);
  }

  public Envelope reply(ResponseCode code) {
    return reply(code, Map.of());
  }
}
