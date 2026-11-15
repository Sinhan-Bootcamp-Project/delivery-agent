package com.sinhan.institution.bank.infrastructure;

import com.sinhan.fep.contract.Bodies.DailyRecord;
import com.sinhan.institution.bank.domain.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAccountRepository implements AccountRepository {
  private static final RowMapper<Account> ACCOUNT =
      (r, i) -> new Account(r.getString(1), r.getString(2), r.getString(3), r.getLong(4));
  private final JdbcTemplate db;

  JdbcAccountRepository(@Qualifier("bankJdbc") JdbcTemplate db) {
    this.db = db;
  }

  @Override
  public Optional<Account> lock(String no) {
    return db.query(
            "select account_no,owner_ref,kind,balance_krw from accounts where account_no=? for update",
            ACCOUNT,
            no)
        .stream()
        .findFirst();
  }

  @Override
  public Optional<Account> find(String no) {
    return db.query(
            "select account_no,owner_ref,kind,balance_krw from accounts where account_no=?", ACCOUNT, no)
        .stream()
        .findFirst();
  }

  @Override
  public Optional<Long> posting(String legId) {
    return db.queryForList("select delta_krw from postings where leg_id=?", Long.class, legId).stream()
        .findFirst();
  }

  @Override
  public void post(String legId, String no, long delta, String memo, LocalDate date) {
    db.update(
        "insert into postings(leg_id,account_no,delta_krw,memo,business_date) values(?,?,?,?,?)",
        legId,
        no,
        delta,
        memo,
        date);
    db.update("update accounts set balance_krw=balance_krw+? where account_no=?", delta, no);
  }

  @Override
  public List<DailyRecord> daily(LocalDate date) {
    return db.query(
        "select leg_id,case when delta_krw<0 then 'WITHDRAW' else 'DEPOSIT' end,abs(delta_krw),account_no"
            + " from postings where business_date=? order by id",
        (r, i) -> new DailyRecord(r.getString(1), r.getString(2), r.getLong(3), r.getString(4)),
        date);
  }
}
