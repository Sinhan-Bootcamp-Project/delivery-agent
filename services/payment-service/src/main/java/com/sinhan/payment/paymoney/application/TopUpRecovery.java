package com.sinhan.payment.paymoney.application;

import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 미확정 충전(REQUESTED/UNKNOWN)을 결제원 상태 조회로 확정한다. */
@Component
public class TopUpRecovery {
  private static final Logger LOG = LoggerFactory.getLogger(TopUpRecovery.class);
  private final PayMoneyService service;

  public TopUpRecovery(PayMoneyService service) {
    this.service = service;
  }

  @Scheduled(fixedDelayString = "${payment.recovery-interval:15000}", initialDelay = 25000)
  public void run() {
    if ("false".equals(System.getenv("PAYMENT_RECOVERY_ENABLED"))) return;
    for (var id : service.staleIds())
      try {
        service.resolve(id);
      } catch (RuntimeException e) {
        LOG.warn("topup recovery deferred id={} errorType={}", id, e.getClass().getSimpleName());
      }
  }
}
