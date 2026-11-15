package com.sinhan.payment.shared;

import com.sinhan.payment.ledger.application.LedgerService;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

/** 시험 전용(test-admin 토큰). 결제 서비스 장애 주입과 원장 조회. */
@RestController
@RequestMapping("/internal/v1/test")
public class TestController {
  private final Faults faults;
  private final LedgerService ledger;
  private final TransactionTemplate tx;

  public TestController(Faults faults, LedgerService ledger, TransactionTemplate tx) {
    this.faults = faults;
    this.ledger = ledger;
    this.tx = tx;
  }

  @PostMapping("/faults/{name}")
  @PreAuthorize("hasAuthority('test:admin')")
  public Map<String, String> fault(@PathVariable String name, @RequestBody Map<String, Integer> body) {
    faults.set(name, body.getOrDefault("count", 1));
    return Map.of("status", "OK");
  }

  /** 분개 목록. amount_krw는 결제(PAYMENT)가 음수, 취소·충전이 양수다(기존 시험 계약 유지). */
  @GetMapping("/ledger")
  @PreAuthorize("hasAuthority('test:admin')")
  public List<Map<String, Object>> ledger(@RequestParam(required = false) String referenceId) {
    return tx.execute(s -> ledger.entries(referenceId));
  }
}
