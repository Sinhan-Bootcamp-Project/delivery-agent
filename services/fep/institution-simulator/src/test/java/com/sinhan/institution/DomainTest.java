package com.sinhan.institution;

import static org.junit.jupiter.api.Assertions.*;

import com.sinhan.fep.contract.Bodies.NetEntry;
import com.sinhan.fep.contract.ResponseCode;
import com.sinhan.institution.bank.domain.Account;
import com.sinhan.institution.bok.domain.SettlementBatch;
import com.sinhan.institution.card.domain.Card;
import com.sinhan.institution.kftc.domain.NetCalculator;
import com.sinhan.institution.kftc.domain.NetCalculator.Movement;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DomainTest {
  @Test
  void cardDeclinesOverLimitAndSuspended() {
    var card = new Card("t", "9410-****-****-0001", 10000, 9000, "ACTIVE");
    assertEquals(ResponseCode.OK, card.authorize(1000));
    assertEquals(ResponseCode.LIMIT_EXCEEDED, card.authorize(1001));
    assertEquals(ResponseCode.SUSPENDED, new Card("t", "m", 10000, 0, "SUSPENDED").authorize(1));
  }

  @Test
  void accountCannotGoNegative() {
    var a = new Account("088-1", "u", "CUSTOMER", 5000);
    assertEquals(ResponseCode.OK, a.canWithdraw(5000));
    assertEquals(ResponseCode.INSUFFICIENT_FUNDS, a.canWithdraw(5001));
  }

  @Test
  void netIgnoresSameBankTransfersAndBalancesToZero() {
    var net =
        NetCalculator.net(
            List.of(
                new Movement("088", "088", 30000),
                new Movement("CRD", "088", 18000),
                new Movement("088", "CRD", 2000)));
    assertEquals(List.of(new NetEntry("088", 16000), new NetEntry("CRD", -16000)), net);
  }

  @Test
  void bokRejectsUnbalancedBatch() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new SettlementBatch(LocalDate.now(), List.of(new NetEntry("088", 1))));
  }
}
