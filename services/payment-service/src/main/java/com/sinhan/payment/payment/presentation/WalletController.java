package com.sinhan.payment.payment.presentation;

import com.sinhan.payment.payment.application.PaymentService;
import com.sinhan.payment.paymoney.application.PayMoneyService;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 결제수단별 사용 가능 금액(혜택 서비스 전용, 기존 계약 유지). CARD_* = 카드사 남은 한도(FEP 조회), LOCAL·PAYMONEY = 페이머니 잔액.
 */
@RestController
@RequestMapping("/internal/v1/wallets")
public class WalletController {
  private final PaymentService payments;
  private final PayMoneyService paymoney;

  public WalletController(PaymentService payments, PayMoneyService paymoney) {
    this.payments = payments;
    this.paymoney = paymoney;
  }

  @GetMapping("/{userId}")
  @PreAuthorize("hasAuthority('wallet:read')")
  public Map<String, Long> wallets(@PathVariable String userId) {
    var result = new LinkedHashMap<>(payments.cardLimits(userId));
    long balance = paymoney.balance(userId);
    result.put("LOCAL", balance);
    result.put("PAYMONEY", balance);
    return result;
  }
}
