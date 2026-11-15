package com.sinhan.payment.ledger.application;

import com.sinhan.payment.ledger.domain.*;
import com.sinhan.payment.ledger.domain.JournalEntry.Kind;
import com.sinhan.payment.ledger.domain.JournalEntry.Line;
import java.util.*;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 원장 컨텍스트의 공개 API. 다른 컨텍스트는 이 클래스로만 기장한다. 계정과목·분개 규칙은 여기서 정한다.
 *
 * <p>모든 메서드는 호출자의 트랜잭션에 참여한다(MANDATORY). 업무 상태 변경과 분개가 같은 커밋이 되어야 하기 때문이다.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class LedgerService {
  private final Ledger ledger;

  public LedgerService(Ledger ledger) {
    this.ledger = ledger;
  }

  /** 결제 승인 분개. 페이머니는 사용자 잔액에서, 카드는 카드사 미수금으로. 즉시할인은 혜택 비용. */
  public void payment(String executionId, String payerAccount, long chargeKrw, long discountKrw) {
    ledger.record(
        new JournalEntry(
            executionId,
            Kind.PAYMENT,
            chargeKrw,
            Stream.of(
                    Line.debit(payerAccount, typeOf(payerAccount), chargeKrw),
                    JournalEntry.debitIfPositive(
                        Accounts.BENEFIT_EXPENSE, AccountType.EXPENSE, discountKrw),
                    Line.credit(
                        Accounts.MERCHANT_PAYABLE, AccountType.LIABILITY, chargeKrw + discountKrw))
                .filter(Objects::nonNull)
                .toList()));
  }

  public void cancel(String executionId) {
    var original =
        ledger
            .find(executionId, Kind.PAYMENT)
            .orElseThrow(() -> new IllegalStateException("no payment entry " + executionId));
    ledger.record(original.reverse(Kind.CANCEL));
  }

  public void topUp(String topUpId, String userId, long amountKrw) {
    ledger.record(
        new JournalEntry(
            topUpId,
            Kind.TOPUP,
            amountKrw,
            List.of(
                Line.debit(Accounts.TRUST_DEPOSIT, AccountType.ASSET, amountKrw),
                Line.credit(Accounts.paymoney(userId), AccountType.LIABILITY, amountKrw))));
  }

  public void cardPayout(String payoutId, long amountKrw) {
    ledger.record(
        new JournalEntry(
            payoutId,
            Kind.CARD_PAYOUT,
            amountKrw,
            List.of(
                Line.debit(Accounts.SETTLEMENT_DEPOSIT, AccountType.ASSET, amountKrw),
                Line.credit(Accounts.CARD_RECEIVABLE, AccountType.ASSET, amountKrw))));
  }

  public boolean hasEntry(String referenceId, Kind kind) {
    return ledger.find(referenceId, kind).isPresent();
  }

  public long paymoneyBalance(String userId) {
    return ledger.balance(Accounts.paymoney(userId));
  }

  public long totalPaymoney() {
    return ledger.sumBalances(Accounts.PAYMONEY_PREFIX);
  }

  public List<String> driftedAccounts() {
    return ledger.drifted();
  }

  public long imbalance() {
    return ledger.imbalance();
  }

  public List<Map<String, Object>> entries(String referenceId) {
    return ledger.entries(referenceId);
  }

  public static String paymoneyAccount(String userId) {
    return Accounts.paymoney(userId);
  }

  public static String cardAccount() {
    return Accounts.CARD_RECEIVABLE;
  }

  private static AccountType typeOf(String account) {
    return account.startsWith(Accounts.PAYMONEY_PREFIX) ? AccountType.LIABILITY : AccountType.ASSET;
  }
}
