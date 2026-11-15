package com.sinhan.institution.bank.domain;

import com.sinhan.fep.contract.Bodies.DailyRecord;
import java.time.LocalDate;
import java.util.*;

public interface AccountRepository {
  Optional<Account> lock(String accountNo);

  Optional<Account> find(String accountNo);

  /** legId 단위 기장 내역. 이미 기장된 구간이면 그 금액(부호 포함). */
  Optional<Long> posting(String legId);

  void post(String legId, String accountNo, long deltaKrw, String memo, LocalDate businessDate);

  List<DailyRecord> daily(LocalDate businessDate);
}
