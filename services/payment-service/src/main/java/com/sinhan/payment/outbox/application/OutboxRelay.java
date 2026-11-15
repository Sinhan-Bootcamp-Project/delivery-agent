package com.sinhan.payment.outbox.application;

import com.sinhan.web.ServiceHttp;
import java.util.*;
import org.slf4j.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 미전달 이벤트를 OUTBOX_TARGET_URL로 POST한다. 주소가 없으면(기본) 전달하지 않고 쌓아 둔다.
 *
 * <p>주문 서비스의 수신 엔드포인트는 아직 없다(주문 담당과 합의 필요). 지금 order-worker는 결제 조회 API로 상태를 확인한다.
 */
@Component
public class OutboxRelay {
  private static final Logger LOG = LoggerFactory.getLogger(OutboxRelay.class);
  private final JdbcTemplate db;
  private final ServiceHttp http;
  private final String target = ServiceHttp.env("OUTBOX_TARGET_URL", "");

  public OutboxRelay(JdbcTemplate db, ServiceHttp http) {
    this.db = db;
    this.http = http;
  }

  @Scheduled(fixedDelayString = "${outbox.relay-interval:2000}")
  public void relay() {
    if (target.isBlank()) return;
    var events =
        db.queryForList(
            "select id,aggregate_id,event_type,payload::text as payload,created_at from outbox_events"
                + " where sent_at is null order by id limit 50");
    for (var e : events) {
      try {
        http.post(
            target,
            Map.of(
                "eventId", e.get("id"),
                "aggregateId", e.get("aggregate_id"),
                "type", e.get("event_type"),
                "payload", e.get("payload"),
                "occurredAt", e.get("created_at").toString()),
            Map.class);
        db.update("update outbox_events set sent_at=now(),attempts=attempts+1 where id=?", e.get("id"));
      } catch (RuntimeException ex) {
        db.update("update outbox_events set attempts=attempts+1 where id=?", e.get("id"));
        LOG.warn("outbox delivery failed id={} errorType={}", e.get("id"), ex.getClass().getSimpleName());
        return;
      }
    }
  }
}
