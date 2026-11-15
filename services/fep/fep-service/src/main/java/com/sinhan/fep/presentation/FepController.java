package com.sinhan.fep.presentation;

import com.sinhan.fep.application.FepService;
import com.sinhan.fep.contract.*;
import com.sinhan.fep.domain.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 단일 전문 엔드포인트. 응답 HTTP 상태는 항상 200이고 결과는 헤더의 응답코드로 판단한다. 9100(TIMEOUT)은 "모름"이다.
 */
@RestController
@RequestMapping("/fep/v1")
public class FepController {
  private final FepService fep;

  public FepController(FepService fep) {
    this.fep = fep;
  }

  @PostMapping("/messages")
  @PreAuthorize("hasAuthority('fep:send')")
  public Envelope send(@RequestBody Envelope request) {
    return fep.send(request);
  }

  public record FaultInput(
      String institution, String messageType, String mode, Long delayMs, String rejectCode, Integer count) {}

  @PostMapping("/test/faults")
  @PreAuthorize("hasAuthority('test:admin')")
  public Map<String, String> fault(@RequestBody FaultInput in) {
    fep.fault(
        new FaultRule(
            Institution.of(in.institution()),
            in.messageType() == null ? null : MessageType.valueOf(in.messageType()),
            FaultMode.valueOf(in.mode()),
            in.delayMs() == null ? 0 : in.delayMs(),
            in.rejectCode(),
            in.count() == null ? 1 : in.count()));
    return Map.of("status", "OK");
  }

  @DeleteMapping("/test/faults")
  @PreAuthorize("hasAuthority('test:admin')")
  public Map<String, String> clear() {
    fep.clearFaults();
    return Map.of("status", "OK");
  }

  @GetMapping("/messages")
  @PreAuthorize("hasAuthority('test:admin') or hasAuthority('fep:read')")
  public List<Map<String, Object>> messages(@RequestParam String transactionId) {
    return fep.messages(transactionId);
  }
}
