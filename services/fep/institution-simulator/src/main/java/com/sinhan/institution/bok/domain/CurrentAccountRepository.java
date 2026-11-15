package com.sinhan.institution.bok.domain;

import java.time.LocalDate;
import java.util.*;

public interface CurrentAccountRepository {
  Optional<SettlementBatch> batch(LocalDate businessDate);

  /** 배치 저장과 당좌계정 증감을 같은 트랜잭션에서 수행한다. */
  void apply(SettlementBatch batch);

  Optional<Long> balance(String institution);
}
