package com.sinhan.institution.bok.infrastructure;

import com.sinhan.institution.shared.Persistence;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** 한국은행 전용 DB 연결(inst_bok 스키마, inst_bok_app 계정). */
@Configuration
class BokPersistence {
  private final Persistence.Context ctx;

  BokPersistence(Environment env) {
    ctx = Persistence.open(env, "bok");
  }

  @Bean
  JdbcTemplate bokJdbc() {
    return new JdbcTemplate(ctx.dataSource());
  }

  @Bean
  TransactionTemplate bokTx() {
    return ctx.tx();
  }
}
