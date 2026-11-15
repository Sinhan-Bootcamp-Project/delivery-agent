package com.sinhan.payment.ledger.domain;

/** 계정 성격. 잔액은 정상 잔액 방향으로 저장한다: 자산·비용은 차변, 부채·자본은 대변이 +. */
public enum AccountType {
  ASSET(true),
  EXPENSE(true),
  LIABILITY(false),
  EQUITY(false);

  public final boolean debitNormal;

  AccountType(boolean debitNormal) {
    this.debitNormal = debitNormal;
  }

  public long delta(long debit, long credit) {
    return debitNormal ? debit - credit : credit - debit;
  }
}
