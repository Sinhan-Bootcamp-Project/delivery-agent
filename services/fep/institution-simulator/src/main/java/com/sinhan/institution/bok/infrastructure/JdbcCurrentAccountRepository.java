package com.sinhan.institution.bok.infrastructure;

import com.sinhan.fep.contract.Bodies.NetEntry;
import com.sinhan.institution.bok.domain.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCurrentAccountRepository implements CurrentAccountRepository {
  private final JdbcTemplate db;

  JdbcCurrentAccountRepository(@Qualifier("bokJdbc") JdbcTemplate db) {
    this.db = db;
  }

  @Override
  public Optional<SettlementBatch> batch(LocalDate date) {
    var entries =
        db.query(
            "select institution,net_krw from batch_entries where business_date=? order by institution",
            (r, i) -> new NetEntry(r.getString(1), r.getLong(2)),
            date);
    Integer batches =
        db.queryForObject("select count(*) from batches where business_date=?", Integer.class, date);
    return batches != null && batches > 0
        ? Optional.of(new SettlementBatch(date, entries))
        : Optional.empty();
  }

  @Override
  public void apply(SettlementBatch b) {
    db.update("insert into batches(business_date) values(?)", b.businessDate());
    for (var e : b.entries()) {
      db.update(
          "insert into batch_entries(business_date,institution,net_krw) values(?,?,?)",
          b.businessDate(),
          e.institution(),
          e.netKrw());
      db.update(
          "update current_accounts set balance_krw=balance_krw+? where institution=?",
          e.netKrw(),
          e.institution());
    }
  }

  @Override
  public Optional<Long> balance(String institution) {
    return db.queryForList(
            "select balance_krw from current_accounts where institution=?", Long.class, institution)
        .stream()
        .findFirst();
  }
}
