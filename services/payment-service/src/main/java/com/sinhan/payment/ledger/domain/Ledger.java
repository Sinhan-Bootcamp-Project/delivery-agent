package com.sinhan.payment.ledger.domain;

import java.util.*;

/** 원장 저장소. 호출자의 트랜잭션 안에서 실행된다. */
public interface Ledger {
  /**
   * 분개를 기록하고 계정 캐시 잔액을 갱신한다. 이미 같은 (referenceId, kind)가 있으면 아무것도 하지 않고 false.
   *
   * @throws InsufficientBalance 음수가 허용되지 않는 계정이 음수가 되는 경우(트랜잭션 롤백 필요)
   */
  boolean record(JournalEntry entry);

  Optional<JournalEntry> find(String referenceId, JournalEntry.Kind kind);

  long balance(String account);

  long sumBalances(String accountPrefix);

  /** 캐시 잔액 ≠ 분개 합인 계정 목록(LED-04). */
  List<String> drifted();

  /** 전체 차변 합 - 대변 합. 항상 0이어야 한다. */
  long imbalance();

  List<Map<String, Object>> entries(String referenceId);

  final class InsufficientBalance extends RuntimeException {
    public final String account;

    public InsufficientBalance(String account) {
      super("insufficient balance: " + account);
      this.account = account;
    }
  }
}
