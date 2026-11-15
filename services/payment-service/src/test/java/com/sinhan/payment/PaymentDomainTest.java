package com.sinhan.payment;

import static org.junit.jupiter.api.Assertions.*;

import com.sinhan.payment.payment.domain.*;
import org.junit.jupiter.api.Test;

class PaymentDomainTest {
  @Test
  void unknownCanOnlyBeSettledToApprovedOrFailed() {
    assertTrue(PaymentStatus.UNKNOWN.canMoveTo(PaymentStatus.APPROVED));
    assertTrue(PaymentStatus.UNKNOWN.canMoveTo(PaymentStatus.FAILED));
    assertFalse(PaymentStatus.UNKNOWN.canMoveTo(PaymentStatus.CANCELLED));
    assertFalse(PaymentStatus.FAILED.canMoveTo(PaymentStatus.APPROVED));
    assertFalse(PaymentStatus.CANCELLED.canMoveTo(PaymentStatus.APPROVED));
  }

  @Test
  void externalStatusKeepsOrderServiceContract() {
    assertEquals("PAID", PaymentStatus.APPROVED.external());
    assertEquals("REFUNDED", PaymentStatus.CANCELLED.external());
    assertEquals("UNKNOWN", PaymentStatus.UNKNOWN.external());
  }

  @Test
  void localCurrencyIsPaidWithPaymoney() {
    assertEquals(PaymentMethod.PAYMONEY, PaymentMethod.from("LOCAL"));
    assertTrue(PaymentMethod.from("CARD_A").card);
    assertThrows(IllegalArgumentException.class, () -> PaymentMethod.from("BITCOIN"));
  }
}
