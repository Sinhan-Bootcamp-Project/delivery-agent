package com.sinhan.fep.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinhan.fep.contract.*;
import com.sinhan.fep.domain.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcFepRepository implements FaultRepository, MessageLog {
  private final JdbcTemplate db;
  private final ObjectMapper json;

  JdbcFepRepository(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  @Override
  public void save(FaultRule r) {
    db.update(
        "insert into faults(institution,message_type,mode,delay_ms,reject_code,remaining) values(?,?,?,?,?,?)"
            + " on conflict(institution,message_type) do update set mode=excluded.mode,delay_ms=excluded.delay_ms,"
            + "reject_code=excluded.reject_code,remaining=excluded.remaining",
        r.institution().code,
        r.messageType() == null ? "*" : r.messageType().name(),
        r.mode().name(),
        r.delayMs(),
        r.rejectCode(),
        r.remaining());
  }

  @Override
  @Transactional
  public Optional<FaultRule> take(Header h) {
    var rows =
        db.queryForList(
            "update faults set remaining=remaining-1 where (institution,message_type)=("
                + "select institution,message_type from faults where institution=? and message_type in (?,'*')"
                + " and remaining>0 order by message_type='*' limit 1 for update)"
                + " returning institution,message_type,mode,delay_ms,reject_code",
            h.institutionCode(),
            h.messageType());
    if (rows.isEmpty()) return Optional.empty();
    var r = rows.getFirst();
    String type = r.get("message_type").toString();
    return Optional.of(
        new FaultRule(
            Institution.of(r.get("institution").toString()),
            type.equals("*") ? null : MessageType.valueOf(type),
            FaultMode.valueOf(r.get("mode").toString()),
            ((Number) r.get("delay_ms")).longValue(),
            r.get("reject_code").toString(),
            0));
  }

  @Override
  public void clear() {
    db.update("delete from faults");
  }

  @Override
  public void request(Envelope e) {
    insert(e, "REQUEST", null, null);
  }

  @Override
  public void response(Envelope e, String fault, long elapsedMs) {
    insert(e, "RESPONSE", fault, elapsedMs);
  }

  private void insert(Envelope e, String direction, String fault, Long elapsed) {
    var h = e.header();
    try {
      db.update(
          "insert into messages(transaction_id,direction,message_type,institution,response_code,body,fault,elapsed_ms)"
              + " values(?,?,?,?,?,cast(? as jsonb),?,?)",
          h.transactionId(),
          direction,
          h.messageType(),
          h.institutionCode(),
          h.responseCode(),
          json.writeValueAsString(Masking.body(e.body() == null ? Map.of() : e.body())),
          fault,
          elapsed);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalArgumentException(ex);
    }
  }

  @Override
  public List<Map<String, Object>> find(String transactionId) {
    return db.queryForList(
        "select id,transaction_id,direction,message_type,institution,response_code,body::text as body,fault,"
            + "elapsed_ms,created_at from messages where transaction_id like ? order by id",
        transactionId + "%");
  }
}
