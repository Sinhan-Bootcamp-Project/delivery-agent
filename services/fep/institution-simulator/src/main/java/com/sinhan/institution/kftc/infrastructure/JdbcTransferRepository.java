package com.sinhan.institution.kftc.infrastructure;

import com.sinhan.fep.contract.Bodies.*;
import com.sinhan.institution.kftc.domain.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTransferRepository implements TransferRepository {
  private static final Set<String> LEGS = Set.of("debit", "credit", "compensation");
  private final JdbcTemplate db;

  JdbcTransferRepository(@Qualifier("kftcJdbc") JdbcTemplate db) {
    this.db = db;
  }

  @Override
  public Optional<TransferOrder> find(String id) {
    return db.query(
            "select transaction_id,from_institution,from_account,to_institution,to_account,amount_krw,purpose,"
                + "status,debit_status,credit_status,compensation_status,failure_code,business_date"
                + " from transfers where transaction_id=?",
            (r, i) ->
                new TransferOrder(
                    r.getString(1),
                    new Transfer(
                        r.getString(2),
                        r.getString(3),
                        r.getString(4),
                        r.getString(5),
                        r.getLong(6),
                        r.getString(7)),
                    r.getString(8),
                    r.getString(9),
                    r.getString(10),
                    r.getString(11),
                    r.getString(12),
                    r.getDate(13).toLocalDate()),
            id)
        .stream()
        .findFirst();
  }

  @Override
  public void createIfAbsent(String id, Transfer t, LocalDate date) {
    db.update(
        "insert into transfers(transaction_id,from_institution,from_account,to_institution,to_account,amount_krw,"
            + "purpose,status,business_date) values(?,?,?,?,?,?,?,'REQUESTED',?) on conflict do nothing",
        id,
        t.fromInstitution(),
        t.fromAccount(),
        t.toInstitution(),
        t.toAccount(),
        t.amountKrw(),
        t.purpose(),
        date);
  }

  @Override
  public void update(String id, String status, String leg, String legStatus, String failure) {
    if (!LEGS.contains(leg)) throw new IllegalArgumentException(leg);
    db.update(
        "update transfers set status=?," + leg + "_status=?,failure_code=coalesce(?,failure_code),"
            + "updated_at=now() where transaction_id=?",
        status,
        legStatus,
        failure,
        id);
  }

  @Override
  public List<NetCalculator.Movement> completedMovements(LocalDate date) {
    return db.query(
        "select from_institution,to_institution,amount_krw from transfers where business_date=? and status='COMPLETED'",
        (r, i) -> new NetCalculator.Movement(r.getString(1), r.getString(2), r.getLong(3)),
        date);
  }

  @Override
  public List<DailyRecord> daily(LocalDate date) {
    return db.query(
        "select transaction_id,purpose,amount_krw,status from transfers where business_date=? order by 1",
        (r, i) -> new DailyRecord(r.getString(1), r.getString(2), r.getLong(3), r.getString(4)),
        date);
  }

  @Override
  public Optional<String> closingStatus(LocalDate date) {
    return db.queryForList("select status from closings where business_date=?", String.class, date)
        .stream()
        .findFirst();
  }

  @Override
  public List<NetEntry> closingEntries(LocalDate date) {
    return db.query(
        "select institution,net_krw from net_entries where business_date=? order by institution",
        (r, i) -> new NetEntry(r.getString(1), r.getLong(2)),
        date);
  }

  @Override
  public void saveClosing(LocalDate date, List<NetEntry> entries, String status) {
    int inserted =
        db.update(
            "insert into closings(business_date,status) values(?,?) on conflict do nothing", date, status);
    if (inserted == 1)
      for (var e : entries)
        db.update(
            "insert into net_entries(business_date,institution,net_krw) values(?,?,?)",
            date,
            e.institution(),
            e.netKrw());
    else db.update("update closings set status=? where business_date=?", status, date);
  }
}
