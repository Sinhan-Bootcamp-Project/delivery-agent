package com.sinhan.payment.payment.presentation;

import com.sinhan.payment.shared.contract.Models.*;
import com.sinhan.payment.payment.application.PaymentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 주문 서비스(order-worker) 전용 내부 API. 응답 계약은 기존과 같다: status = PAID | REFUNDED | PENDING | UNKNOWN |
 * CANCEL_PENDING.
 */
@RestController
@RequestMapping("/internal/v1/payments")
public class PaymentController {
  private final PaymentService payments;

  public PaymentController(PaymentService payments) {
    this.payments = payments;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('payment:charge')")
  public PaymentResult charge(@RequestBody ExecutionCommand c) {
    return payments.charge(c);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('payment:read')")
  public PaymentResult get(@PathVariable String id) {
    return payments.lookup(id);
  }

  /** 전체 취소. /refund는 기존 호출자 호환용 별칭. */
  @PostMapping({"/{id}/cancel", "/{id}/refund"})
  @PreAuthorize("hasAuthority('payment:refund')")
  public PaymentResult cancel(@PathVariable String id) {
    return payments.cancel(id);
  }
}
