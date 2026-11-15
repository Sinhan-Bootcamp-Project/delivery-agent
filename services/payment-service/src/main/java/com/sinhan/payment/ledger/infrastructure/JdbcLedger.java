package com.sinhan.payment.ledger.infrastructure;

import com.sinhan.payment.ledger.domain.*;
import com.sinhan.payment.ledger.domain.JournalEntry.Kind;
import com.sinhan.payment.ledger.domain.JournalEntry.Line;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * journal_entries / journal_lines는 DB 트리거로 UPDATE·DELETE가 막혀 있다. accounts.balance_krw는 분개 합의 캐시이며
 * PAYMONEY 계정은 CHECK로 음수가 막혀 있다.
 */
@Repository
class JdbcLedger implements Ledger {
  private final JdbcTemplate db;

  JdbcLedger(JdbcTemplate db) {
    this.db = db;
  }

  @Override
  public boolean record(JournalEntry e) {
    var ids =
        db.queryForList(
            "insert into journal_entries(reference_id,kind,amount_krw) values(?,?,?)"
                + " on conflict(reference_id,kind) do nothing returning id",
            Long.class,
            e.referenceId(),
            e.kind().name(),
            e.amountKrw());
    if (ids.isEmpty()) return false;
    long id = ids.getFirst();
    // 잠금 순서를 고정해 교착을 피한다.
    var lines = new ArrayList<>(e.lines());
    lines.sort(Comparator.comparing(Line::account));
    for (var l : lines) {
      db.update(
          "insert into accounts(code,type,allow_negative) values(?,?,?) on conflict do nothing",
          l.account(),
          l.type().name(),
          !l.account().startsWith(Accounts.PAYMONEY_PREFIX));
      int changed =
          db.update(
              "update accounts set balance_krw=balance_krw+? where code=? and (allow_negative or balance_krw+?>=0)",
              l.balanceDelta(),
              l.account(),
              l.balanceDelta());
      if (changed != 1) throw new InsufficientBalance(l.account());
      db.update(
          "insert into journal_lines(entry_id,account_code,debit_krw,credit_krw) values(?,?,?,?)",
          id,
          l.account(),
          l.debitKrw(),
          l.creditKrw());
    }
    return true;
  }

  @Override
  public Optional<JournalEntry> find(String referenceId, Kind kind) {
    var rows =
        db.queryForList(
            "select e.amount_krw,l.account_code,a.type,l.debit_krw,l.credit_krw from journal_entries e"
                + " join journal_lines l on l.entry_id=e.id join accounts a on a.code=l.account_code"
                + " where e.reference_id=? and e.kind=? order by l.id",
            referenceId,
            kind.name());
    if (rows.isEmpty()) return Optional.empty();
    var lines =
        rows.stream()
            .map(
                r ->
                    new Line(
                        r.get("account_code").toString(),
                        AccountType.valueOf(r.get("type").toString()),
                        ((Number) r.get("debit_krw")).longValue(),
                        ((Number) r.get("credit_krw")).longValue()))
            .toList();
    return Optional.of(
        new JournalEntry(
            referenceId, kind, ((Number) rows.getFirst().get("amount_krw")).longValue(), lines));
  }

  @Override
  public long balance(String account) {
    return db.queryForList("select balance_krw from accounts where code=?", Long.class, account).stream()
        .findFirst()
        .orElse(0L);
  }

  @Override
  public long sumBalances(String prefix) {
    Long v =
        db.queryForObject(
            "select coalesce(sum(balance_krw),0) from accounts where code like ?", Long.class, prefix + "%");
    return v == null ? 0 : v;
  }

  @Override
  public List<String> drifted() {
    return db.queryForList(
        "select a.code from accounts a left join journal_lines l on l.account_code=a.code group by a.code,a.type,a.balance_krw"
            + " having a.balance_krw <> case when a.type in ('ASSET','EXPENSE')"
            + " then coalesce(sum(l.debit_krw-l.credit_krw),0) else coalesce(sum(l.credit_krw-l.debit_krw),0) end",
        String.class);
  }

  @Override
  public long imbalance() {
    Long v =
        db.queryForObject("select coalesce(sum(debit_krw-credit_krw),0) from journal_lines", Long.class);
    return v == null ? 0 : v;
  }

  @Override
  public List<Map<String, Object>> entries(String referenceId) {
    return db.queryForList(
        "select e.id as entry_id,e.reference_id as execution_id,e.kind,"
            + " case when e.kind='PAYMENT' then -e.amount_krw else e.amount_krw end as amount_krw,e.created_at"
            + " from journal_entries e where (cast(? as text) is null or e.reference_id=cast(? as text)) and e.kind<>'OPENING' order by e.id",
        referenceId,
        referenceId);
  }
}
