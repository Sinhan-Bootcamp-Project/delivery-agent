package com.sinhan.payment.payment.application;

import com.sinhan.payment.payment.domain.PaymentStatus;
import java.util.EnumSet;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 미확정 결제 복구(F-07). PENDING(서버가 FEP 호출 전후에 죽은 경우)과 UNKNOWN을 상태 조회로 확정하고, 멈춘 취소를 이어서 처리한다.
 * 끝내 모르는 건은 23:30 대사가 확정한다.
 */
@Component
public class PaymentRecovery {
  private static final Logger LOG = LoggerFactory.getLogger(PaymentRecovery.class);
  private final PaymentService payments;

  public PaymentRecovery(PaymentService payments) {
    this.payments = payments;
  }

  @Scheduled(fixedDelayString = "${payment.recovery-interval:15000}", initialDelay = 20000)
  public void run() {
    if ("false".equals(System.getenv("PAYMENT_RECOVERY_ENABLED"))) return;
    for (var id : payments.staleIds(EnumSet.of(PaymentStatus.PENDING, PaymentStatus.UNKNOWN)))
      safely(id, () -> payments.resolve(id));
    for (var id : payments.staleIds(EnumSet.of(PaymentStatus.CANCEL_PENDING)))
      safely(id, () -> payments.cancel(id));
  }

  private static void safely(String id, Runnable r) {
    try {
      r.run();
    } catch (RuntimeException e) {
      LOG.warn("payment recovery deferred id={} errorType={}", id, e.getClass().getSimpleName());
    }
  }
}
