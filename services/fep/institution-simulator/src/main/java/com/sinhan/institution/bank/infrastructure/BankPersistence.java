package com.sinhan.institution.bank.infrastructure;

import com.sinhan.institution.shared.Persistence;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** 은행 전용 DB 연결(inst_bank 스키마, inst_bank_app 계정). */
@Configuration
class BankPersistence {
  private final Persistence.Context ctx;

  BankPersistence(Environment env) {
    ctx = Persistence.open(env, "bank");
  }

  @Bean
  JdbcTemplate bankJdbc() {
    return new JdbcTemplate(ctx.dataSource());
  }

  @Bean
  TransactionTemplate bankTx() {
    return ctx.tx();
  }
}
