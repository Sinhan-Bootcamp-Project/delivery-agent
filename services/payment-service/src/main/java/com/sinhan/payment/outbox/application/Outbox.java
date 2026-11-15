package com.sinhan.payment.outbox.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox(F-09, REC-05). 상태 변경과 같은 트랜잭션에서 이벤트를 저장한다. 전달은 {@link OutboxRelay}가 따로 한다(적어도 한 번).
 * 소비자는 eventId로 중복을 걸러야 한다.
 */
@Service
public class Outbox {
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public Outbox(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void publish(String aggregateId, String type, Map<String, Object> payload) {
    try {
      db.update(
          "insert into outbox_events(aggregate_id,event_type,payload) values(?,?,cast(? as jsonb))"
              + " on conflict(aggregate_id,event_type) do nothing",
          aggregateId,
          type,
          json.writeValueAsString(payload));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalArgumentException(e);
    }
  }
}
