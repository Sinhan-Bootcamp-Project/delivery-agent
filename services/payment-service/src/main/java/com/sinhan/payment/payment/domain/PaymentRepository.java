package com.sinhan.payment.payment.domain;

import java.time.*;
import java.util.*;

public interface PaymentRepository {
  /** 같은 거래ID 처리를 직렬화한다(트랜잭션 범위 advisory lock). */
  void lock(String executionId);

  Optional<Payment> find(String executionId);

  void insert(Payment p);

  void changeStatus(String executionId, PaymentStatus from, PaymentStatus to, String approvalNo, String failureCode);

  void markCancelled(String executionId, PaymentStatus from, LocalDate businessDate);

  List<String> stale(Collection<PaymentStatus> statuses, Instant before);

  List<Payment> cardPaymentsTouching(LocalDate businessDate);

  Optional<String> cardToken(String userId, PaymentMethod method);
}
