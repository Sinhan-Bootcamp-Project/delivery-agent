package com.sinhan.institution.shared;

import com.sinhan.fep.contract.Envelope;
import com.sinhan.web.Problem;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** FEP만 호출하는 전문 수신 엔드포인트. Mock 네트워크에만 붙어 있어 한끼 서비스는 직접 닿지 않는다. */
@RestController
@RequestMapping("/institutions/v1")
public class InstitutionController {
  private final InstitutionRouter router;
  private final Faults faults;

  public InstitutionController(InstitutionRouter router, Faults faults) {
    this.router = router;
    this.faults = faults;
  }

  @PostMapping("/messages")
  @PreAuthorize("hasAuthority('institution:message')")
  public Envelope receive(@RequestBody Envelope request) {
    return router.receive(request, Channel.FEP);
  }

  @PostMapping("/test/faults/{name}")
  @PreAuthorize("hasAuthority('institution:message') or hasAuthority('test:admin')")
  public Map<String, String> fault(
      @PathVariable String name, @RequestBody Map<String, Integer> body) {
    if (!faults.enabled()) throw new Problem(404, "NOT_FOUND", "찾을 수 없습니다.");
    faults.set(name, body.getOrDefault("count", 1));
    return Map.of("status", "OK");
  }
}
