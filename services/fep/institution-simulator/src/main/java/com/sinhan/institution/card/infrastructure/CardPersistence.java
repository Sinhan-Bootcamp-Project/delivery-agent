package com.sinhan.institution.card.infrastructure;

import com.sinhan.institution.shared.Persistence;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** 카드사 전용 DB 연결(inst_card 스키마, inst_card_app 계정). */
@Configuration
class CardPersistence {
  private final Persistence.Context ctx;

  CardPersistence(Environment env) {
    ctx = Persistence.open(env, "card");
  }

  @Bean
  JdbcTemplate cardJdbc() {
    return new JdbcTemplate(ctx.dataSource());
  }

  @Bean
  TransactionTemplate cardTx() {
    return ctx.tx();
  }
}
