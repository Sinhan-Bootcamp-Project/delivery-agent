package com.sinhan.payment.shared;

import com.sinhan.web.Problem;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 결제 서비스 자체 장애 주입(시험 전용). 기관·FEP 장애는 fep-service의 /fep/v1/test/faults를 쓴다. */
@Component
public class Faults {
  public static final Set<String> NAMES =
      Set.of("charge_response_lost", "refund_unavailable", "refund_response_lost");
  private final JdbcTemplate db;

  public Faults(JdbcTemplate db) {
    this.db = db;
  }

  public boolean take(String name) {
    return db.update("update faults set remaining=remaining-1 where name=? and remaining>0", name) == 1;
  }

  public void set(String name, int count) {
    if (!"true".equals(System.getenv("ENABLE_FAULTS")))
      throw new Problem(404, "NOT_FOUND", "찾을 수 없습니다.");
    if (!NAMES.contains(name) || count < 0 || count > 100)
      throw new Problem(400, "INVALID_FAULT", "잘못된 시나리오입니다.");
    db.update(
        "insert into faults(name,remaining) values(?,?) on conflict(name) do update set remaining=excluded.remaining",
        name,
        count);
  }
}
