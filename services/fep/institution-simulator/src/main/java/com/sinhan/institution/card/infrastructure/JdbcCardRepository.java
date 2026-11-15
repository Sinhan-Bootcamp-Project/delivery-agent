package com.sinhan.institution.card.infrastructure;

import com.sinhan.fep.contract.Bodies.DailyRecord;
import com.sinhan.institution.card.domain.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCardRepository implements CardRepository {
  private final JdbcTemplate db;

  JdbcCardRepository(@Qualifier("cardJdbc") JdbcTemplate db) {
    this.db = db;
  }

  @Override
  public Optional<Card> lockCard(String token) {
    return db
        .query(
            "select token,masked_no,limit_krw,used_krw,status from cards where token=? for update",
            (r, i) ->
                new Card(
                    r.getString(1), r.getString(2), r.getLong(3), r.getLong(4), r.getString(5)),
            token)
        .stream()
        .findFirst();
  }

  @Override
  public Optional<Authorization> authorization(String id) {
    return db
        .query(
            "select transaction_id,card_token,amount_krw,approval_no,status,business_date,cancel_business_date"
                + " from authorizations where transaction_id=?",
            (r, i) -> map(r),
            id)
        .stream()
        .findFirst();
  }

  private static Authorization map(ResultSet r) throws SQLException {
    var cancel = r.getDate(7);
    return new Authorization(
        r.getString(1),
        r.getString(2),
        r.getLong(3),
        r.getString(4),
        r.getString(5),
        r.getDate(6).toLocalDate(),
        cancel == null ? null : cancel.toLocalDate());
  }

  @Override
  public void approve(Authorization a) {
    db.update(
        "insert into authorizations(transaction_id,card_token,amount_krw,approval_no,status,business_date)"
            + " values(?,?,?,?,?,?)",
        a.transactionId(),
        a.cardToken(),
        a.amountKrw(),
        a.approvalNo(),
        a.status(),
        a.businessDate());
  }

  @Override
  public void cancel(String id, LocalDate date) {
    db.update(
        "update authorizations set status='CANCELLED',cancel_business_date=? where transaction_id=?",
        date,
        id);
  }

  @Override
  public void changeUsed(String token, long delta) {
    db.update("update cards set used_krw=used_krw+? where token=?", delta, token);
  }

  @Override
  public List<DailyRecord> daily(LocalDate date) {
    return db.query(
        "select transaction_id,'APPROVAL',amount_krw,status from authorizations where business_date=?"
            + " union all select transaction_id,'CANCEL',amount_krw,status from authorizations"
            + " where cancel_business_date=? order by 1,2",
        (r, i) -> new DailyRecord(r.getString(1), r.getString(2), r.getLong(3), r.getString(4)),
        date,
        date);
  }

  @Override
  public long payoutAmount(LocalDate date) {
    Long v =
        db.queryForObject(
            "select coalesce(sum(amount_krw) filter (where business_date=?),0)"
                + " - coalesce(sum(amount_krw) filter (where cancel_business_date=?),0) from authorizations",
            Long.class,
            date,
            date);
    return v == null ? 0 : v;
  }

  @Override
  public Optional<Long> payout(LocalDate date) {
    return db.queryForList("select amount_krw from payouts where business_date=?", Long.class, date)
        .stream()
        .findFirst();
  }

  @Override
  public void recordPayout(LocalDate date, long amount, String status) {
    db.update(
        "insert into payouts(business_date,amount_krw,status) values(?,?,?)"
            + " on conflict(business_date) do update set status=excluded.status",
        date,
        amount,
        status);
  }

  @Override
  public void recordSettlementLeg(String legId, long amount) {
    db.update(
        "insert into settlement_legs(leg_id,amount_krw) values(?,?) on conflict do nothing",
        legId,
        amount);
  }
}
