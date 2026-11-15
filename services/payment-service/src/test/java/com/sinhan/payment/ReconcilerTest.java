package com.sinhan.payment;

import static org.junit.jupiter.api.Assertions.*;

import com.sinhan.payment.settlement.domain.Reconciler;
import com.sinhan.payment.settlement.domain.Reconciler.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReconcilerTest {
  @Test
  void classifiesEveryMismatchType() {
    var local =
        List.of(
            new Item("same", "APPROVAL", 1000, "APPROVED"),
            new Item("amount", "APPROVAL", 1000, "APPROVED"),
            new Item("status", "APPROVAL", 1000, "APPROVED"),
            new Item("onlyHanki", "APPROVAL", 1000, "APPROVED"),
            new Item("failedAbsent", "APPROVAL", 1000, "FAILED"),
            new Item("unknownApproved", "APPROVAL", 1000, "UNKNOWN"),
            new Item("unknownAbsent", "APPROVAL", 1000, "UNKNOWN"));
    var remote =
        List.of(
            new Item("same", "APPROVAL", 1000, "APPROVED"),
            new Item("amount", "APPROVAL", 900, "APPROVED"),
            new Item("status", "APPROVAL", 1000, "CANCELLED"),
            new Item("onlyCard", "APPROVAL", 1000, "APPROVED"),
            new Item("unknownApproved", "APPROVAL", 1000, "APPROVED"));
    var findings = Reconciler.compare(local, remote);
    assertEquals(Kind.AMOUNT_MISMATCH, kind(findings, "amount"));
    assertEquals(Kind.STATUS_MISMATCH, kind(findings, "status"));
    assertEquals(Kind.MISSING_AT_INSTITUTION, kind(findings, "onlyHanki"));
    assertEquals(Kind.MISSING_AT_HANKI, kind(findings, "onlyCard"));
    assertEquals(Kind.RESOLVE_UNKNOWN, kind(findings, "unknownApproved"));
    assertNotNull(find(findings, "unknownApproved").remote());
    assertNull(find(findings, "unknownAbsent").remote());
    assertNull(find(findings, "same"));
    assertNull(find(findings, "failedAbsent"));
  }

  private static Finding find(List<Finding> f, String id) {
    return f.stream().filter(x -> x.transactionId().equals(id)).findFirst().orElse(null);
  }

  private static Kind kind(List<Finding> f, String id) {
    return find(f, id).kind();
  }
}
