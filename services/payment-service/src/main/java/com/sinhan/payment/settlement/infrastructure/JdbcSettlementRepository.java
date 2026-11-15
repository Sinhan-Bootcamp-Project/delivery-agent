package com.sinhan.payment.settlement.infrastructure;

import com.sinhan.payment.settlement.domain.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSettlementRepository implements SettlementRepository {
  private final JdbcTemplate db;

  JdbcSettlementRepository(JdbcTemplate db) {
    this.db = db;
  }

  @Override
  public boolean start(LocalDate date) {
    db.update(
        "insert into settlement_runs(business_date,status) values(?,'RUNNING') on conflict do nothing", date);
    return db.update(
            "update settlement_runs set status='RUNNING',error=null,updated_at=now() where business_date=? and status<>'COMPLETED'",
            date)
        == 1;
  }

  @Override
  public Set<Step> completedSteps(LocalDate date) {
    var steps = EnumSet.noneOf(Step.class);
    db.queryForList("select step from settlement_steps where business_date=?", String.class, date)
        .forEach(s -> steps.add(Step.valueOf(s)));
    return steps;
  }

  @Override
  public void stepDone(LocalDate date, Step step, String detail) {
    db.update(
        "insert into settlement_steps(business_date,step,detail) values(?,?,?) on conflict do nothing",
        date,
        step.name(),
        detail);
  }

  @Override
  public void failed(LocalDate date, Step step, String error) {
    db.update(
        "update settlement_runs set status='FAILED',failed_step=?,error=?,updated_at=now() where business_date=?",
        step.name(),
        error,
        date);
  }

  @Override
  public void completed(LocalDate date, String status) {
    db.update(
        "update settlement_runs set status=?,failed_step=null,error=null,updated_at=now() where business_date=?",
        status,
        date);
  }

  @Override
  public void finding(LocalDate date, Step step, Reconciler.Finding f, String resolution) {
    db.update(
        "insert into reconciliation_items(business_date,step,transaction_id,record_type,kind,hanki_amount_krw,hanki_status,"
            + "institution_amount_krw,institution_status,resolution) values(?,?,?,?,?,?,?,?,?,?)"
            + " on conflict(business_date,step,transaction_id,record_type) do update set kind=excluded.kind,"
            + "resolution=excluded.resolution",
        date,
        step.name(),
        f.transactionId(),
        f.type(),
        f.kind().name(),
        f.local() == null ? null : f.local().amountKrw(),
        f.local() == null ? null : f.local().status(),
        f.remote() == null ? null : f.remote().amountKrw(),
        f.remote() == null ? null : f.remote().status(),
        resolution);
  }

  @Override
  public Map<String, Object> report(LocalDate date) {
    var runs = db.queryForList("select * from settlement_runs where business_date=?", date);
    var report = new LinkedHashMap<String, Object>();
    report.put("businessDate", date.toString());
    report.put("run", runs.isEmpty() ? null : runs.getFirst());
    report.put(
        "steps",
        db.queryForList(
            "select step,detail,created_at from settlement_steps where business_date=? order by created_at", date));
    report.put(
        "items",
        db.queryForList("select * from reconciliation_items where business_date=? order by id", date));
    return report;
  }
}
