package com.sinhan.payment.paymoney.presentation;

import com.sinhan.payment.paymoney.application.PayMoneyService;
import com.sinhan.payment.paymoney.application.PayMoneyService.TopUpView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** 사용자 공개 API(Gateway 경유, 사용자 JWT). 사용자 ID는 토큰 subject에서만 꺼낸다. */
@RestController
@RequestMapping("/v1/paymoney")
public class PayMoneyController {
  private final PayMoneyService service;

  public PayMoneyController(PayMoneyService service) {
    this.service = service;
  }

  public record TopUpInput(@NotNull @Min(1000) @Max(1000000) Long amountKrw) {}

  @GetMapping("/balance")
  @PreAuthorize("hasAuthority('customer')")
  public Map<String, Long> balance(@AuthenticationPrincipal Jwt jwt) {
    return Map.of("balanceKrw", service.balance(jwt.getSubject()));
  }

  @PostMapping("/charges")
  @PreAuthorize("hasAuthority('customer')")
  public TopUpView charge(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      @Valid @RequestBody TopUpInput in) {
    return service.topUp(jwt.getSubject(), key, in.amountKrw());
  }

  @GetMapping("/charges/{id}")
  @PreAuthorize("hasAuthority('customer')")
  public TopUpView get(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
    return service.find(jwt.getSubject(), id);
  }
}
