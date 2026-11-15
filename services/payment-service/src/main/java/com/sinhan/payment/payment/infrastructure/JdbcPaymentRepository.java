package com.sinhan.payment.payment.infrastructure;

import com.sinhan.payment.payment.domain.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPaymentRepository implements PaymentRepository {
  private static final String COLUMNS =
      "execution_id,user_id,requested_method,method,amount_krw,discount_krw,digest,status,approval_no,"
          + "failure_code,business_date,cancel_business_date,updated_at";
  private static final RowMapper<Payment> MAPPER = (r, i) -> map(r);
  private final JdbcTemplate db;

  JdbcPaymentRepository(JdbcTemplate db) {
    this.db = db;
  }

  private static Payment map(ResultSet r) throws SQLException {
    var cancel = r.getDate("cancel_business_date");
    return new Payment(
        r.getString("execution_id"),
        r.getString("user_id"),
        r.getString("requested_method"),
        PaymentMethod.valueOf(r.getString("method")),
        r.getLong("amount_krw"),
        r.getLong("discount_krw"),
        r.getString("digest"),
        PaymentStatus.valueOf(r.getString("status")),
        r.getString("approval_no"),
        r.getString("failure_code"),
        r.getDate("business_date").toLocalDate(),
        cancel == null ? null : cancel.toLocalDate(),
        r.getTimestamp("updated_at").toInstant());
  }

  @Override
  public void lock(String id) {
    db.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))", id);
  }

  @Override
  public Optional<Payment> find(String id) {
    return db.query("select " + COLUMNS + " from payments where execution_id=?", MAPPER, id).stream()
        .findFirst();
  }

  @Override
  public void insert(Payment p) {
    db.update(
        "insert into payments(execution_id,user_id,requested_method,method,amount_krw,discount_krw,digest,status,business_date)"
            + " values(?,?,?,?,?,?,?,?,?)",
        p.executionId(),
        p.userId(),
        p.requestedMethod(),
        p.method().name(),
        p.amountKrw(),
        p.discountKrw(),
        p.digest(),
        p.status().name(),
        p.businessDate());
    history(p.executionId(), null, p.status());
  }

  @Override
  public void changeStatus(String id, PaymentStatus from, PaymentStatus to, String approvalNo, String failure) {
    int n =
        db.update(
            "update payments set status=?,approval_no=coalesce(?,approval_no),failure_code=?,updated_at=now()"
                + " where execution_id=? and status=?",
            to.name(),
            approvalNo,
            failure,
            id,
            from.name());
    if (n != 1) throw new IllegalStateException("concurrent status change " + id);
    history(id, from, to);
  }

  @Override
  public void markCancelled(String id, PaymentStatus from, LocalDate date) {
    int n =
        db.update(
            "update payments set status='CANCELLED',cancel_business_date=?,updated_at=now() where execution_id=? and status=?",
            date,
            id,
            from.name());
    if (n != 1) throw new IllegalStateException("concurrent cancel " + id);
    history(id, from, PaymentStatus.CANCELLED);
  }

  private void history(String id, PaymentStatus from, PaymentStatus to) {
    db.update(
        "insert into payment_history(execution_id,from_status,to_status) values(?,?,?)",
        id,
        from == null ? null : from.name(),
        to.name());
  }

  @Override
  public List<String> stale(Collection<PaymentStatus> statuses, Instant before) {
    return db.queryForList(
        "select execution_id from payments where status = any(?) and updated_at < ? order by updated_at limit 100",
        String.class,
        statuses.stream().map(Enum::name).toArray(String[]::new),
        Timestamp.from(before));
  }

  @Override
  public List<Payment> cardPaymentsTouching(LocalDate date) {
    return db.query(
        "select " + COLUMNS + " from payments where method<>'PAYMONEY' and (business_date=? or cancel_business_date=?)",
        MAPPER,
        date,
        date);
  }

  @Override
  public Optional<String> cardToken(String userId, PaymentMethod method) {
    return db.queryForList(
            "select card_token from card_links where user_id=? and method=?", String.class, userId, method.name())
        .stream()
        .findFirst();
  }
}
