package com.sinhan.payment.paymoney.infrastructure;

import com.sinhan.payment.paymoney.domain.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTopUpRepository implements TopUpRepository {
  private static final RowMapper<TopUp> MAPPER =
      (r, i) ->
          new TopUp(
              r.getString("id"),
              r.getString("user_id"),
              r.getString("bank_account_no"),
              r.getLong("amount_krw"),
              r.getString("status"),
              r.getString("failure_code"),
              r.getDate("business_date").toLocalDate(),
              r.getTimestamp("updated_at").toInstant());
  private final JdbcTemplate db;

  JdbcTopUpRepository(JdbcTemplate db) {
    this.db = db;
  }

  @Override
  public void lock(String id) {
    db.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))", id);
  }

  @Override
  public Optional<TopUp> find(String id) {
    return db.query("select * from topups where id=?", MAPPER, id).stream().findFirst();
  }

  @Override
  public void insert(TopUp t) {
    db.update(
        "insert into topups(id,user_id,bank_account_no,amount_krw,status,business_date) values(?,?,?,?,?,?)",
        t.id(),
        t.userId(),
        t.bankAccountNo(),
        t.amountKrw(),
        t.status(),
        t.businessDate());
  }

  @Override
  public void changeStatus(String id, String from, String to, String failure) {
    int n =
        db.update(
            "update topups set status=?,failure_code=?,updated_at=now() where id=? and status=?", to, failure, id, from);
    if (n != 1) throw new IllegalStateException("concurrent topup change " + id);
  }

  @Override
  public List<String> stale(Instant before) {
    return db.queryForList(
        "select id from topups where status in ('REQUESTED','UNKNOWN') and updated_at < ? order by updated_at limit 100",
        String.class,
        Timestamp.from(before));
  }

  @Override
  public List<TopUp> byBusinessDate(LocalDate date) {
    return db.query("select * from topups where business_date=?", MAPPER, date);
  }

  @Override
  public Optional<String> linkedAccount(String userId) {
    return db.queryForList("select bank_account_no from paymoney_links where user_id=?", String.class, userId)
        .stream()
        .findFirst();
  }
}
