package com.sinhan.payment;

import static org.junit.jupiter.api.Assertions.*;

import com.sinhan.payment.ledger.domain.*;
import com.sinhan.payment.ledger.domain.JournalEntry.*;
import com.sinhan.payment.ledger.application.LedgerService;
import java.util.*;
import org.junit.jupiter.api.Test;

class LedgerDomainTest {
  @Test
  void rejectsUnbalancedEntry() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new JournalEntry(
                "x",
                Kind.PAYMENT,
                100,
                List.of(
                    Line.debit("PAYMONEY:u", AccountType.LIABILITY, 100),
                    Line.credit(Accounts.MERCHANT_PAYABLE, AccountType.LIABILITY, 90))));
  }

  @Test
  void paymentWithDiscountBalancesAndReverses() {
    var e =
        new JournalEntry(
            "x",
            Kind.PAYMENT,
            16000,
            List.of(
                Line.debit("PAYMONEY:u", AccountType.LIABILITY, 16000),
                Line.debit(Accounts.BENEFIT_EXPENSE, AccountType.EXPENSE, 2000),
                Line.credit(Accounts.MERCHANT_PAYABLE, AccountType.LIABILITY, 18000)));
    assertEquals(-16000, e.lines().getFirst().balanceDelta(), "부채 계정 차변은 잔액 감소");
    var r = e.reverse(Kind.CANCEL);
    assertEquals(16000, r.lines().getFirst().balanceDelta());
    assertEquals(
        0,
        e.lines().stream().mapToLong(Line::balanceDelta).sum()
            + r.lines().stream().mapToLong(Line::balanceDelta).sum());
  }

  @Test
  void zeroAmountLinesAreNotAllowed() {
    assertThrows(IllegalArgumentException.class, () -> Line.debit("a", AccountType.ASSET, 0));
    assertNull(JournalEntry.debitIfPositive("a", AccountType.ASSET, 0));
  }

  @Test
  void paymentWithoutDiscountHasNoBenefitLine() {
    var recorded = new ArrayList<JournalEntry>();
    Ledger fake =
        new Ledger() {
          public boolean record(JournalEntry e) {
            return recorded.add(e);
          }

          public Optional<JournalEntry> find(String r, Kind k) {
            return Optional.empty();
          }

          public long balance(String a) {
            return 0;
          }

          public long sumBalances(String p) {
            return 0;
          }

          public List<String> drifted() {
            return List.of();
          }

          public long imbalance() {
            return 0;
          }

          public List<Map<String, Object>> entries(String r) {
            return List.of();
          }
        };
    new LedgerService(fake).payment("x", Accounts.CARD_RECEIVABLE, 17000, 0);
    assertEquals(2, recorded.getFirst().lines().size());
  }
}
