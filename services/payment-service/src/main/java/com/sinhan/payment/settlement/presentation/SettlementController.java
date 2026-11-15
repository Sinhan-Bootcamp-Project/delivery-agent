package com.sinhan.payment.settlement.presentation;

import com.sinhan.payment.settlement.application.SettlementService;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 일 마감 수동 실행·리포트 조회(운영·시연용). 정기 실행은 23:30 스케줄러. */
@RestController
@RequestMapping("/internal/v1/settlements")
public class SettlementController {
  private final SettlementService settlement;

  public SettlementController(SettlementService settlement) {
    this.settlement = settlement;
  }

  @PostMapping("/{businessDate}/run")
  @PreAuthorize("hasAuthority('settlement:run') or hasAuthority('test:admin')")
  public Map<String, Object> run(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
    return settlement.run(businessDate);
  }

  @GetMapping("/{businessDate}")
  @PreAuthorize("hasAuthority('settlement:run') or hasAuthority('test:admin')")
  public Map<String, Object> report(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
    return settlement.report(businessDate);
  }
}
