package com.sinhan.institution.kftc.infrastructure;

import com.sinhan.institution.shared.Persistence;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** 결제원 전용 DB 연결(inst_kftc 스키마, inst_kftc_app 계정). */
@Configuration
class KftcPersistence {
  private final Persistence.Context ctx;

  KftcPersistence(Environment env) {
    ctx = Persistence.open(env, "kftc");
  }

  @Bean
  JdbcTemplate kftcJdbc() {
    return new JdbcTemplate(ctx.dataSource());
  }

  @Bean
  TransactionTemplate kftcTx() {
    return ctx.tx();
  }
}
