package com.sinhan.fep.application;

import com.sinhan.fep.contract.*;
import com.sinhan.fep.domain.*;
import com.sinhan.web.Problem;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FepService {
  private final Relay.Transport transport;
  private final FaultRepository faults;
  private final MessageLog log;
  private final Duration timeout;

  public FepService(
      Relay.Transport transport,
      FaultRepository faults,
      MessageLog log,
      @Value("${fep.institution-timeout:3s}") Duration timeout) {
    this.transport = transport;
    this.faults = faults;
    this.log = log;
    this.timeout = timeout;
  }

  public Envelope send(Envelope request) {
    validate(request);
    log.request(request);
    long start = System.nanoTime();
    var rule = faultsEnabled() ? faults.take(request.header()).orElse(null) : null;
    var outcome = Relay.relay(request, rule, transport, timeout);
    log.response(outcome.response(), outcome.fault(), (System.nanoTime() - start) / 1_000_000);
    return outcome.response();
  }

  private static void validate(Envelope e) {
    var h = e.header();
    if (h == null || h.transactionId() == null || !h.transactionId().matches("[A-Za-z0-9:_-]{1,80}"))
      throw new Problem(400, "INVALID_HEADER", "전문 헤더를 확인해주세요.");
    try {
      h.type();
      h.institution();
    } catch (RuntimeException ex) {
      throw new Problem(400, "INVALID_HEADER", "전문종별 또는 기관코드가 잘못되었습니다.");
    }
    if (h.type() == MessageType.NET_SETTLEMENT || h.type() == MessageType.WITHDRAW || h.type() == MessageType.DEPOSIT)
      throw new Problem(403, "NOT_ROUTABLE", "기관 간 전문은 FEP로 보낼 수 없습니다.");
  }

  public void fault(FaultRule rule) {
    if (!faultsEnabled()) throw new Problem(404, "NOT_FOUND", "찾을 수 없습니다.");
    faults.save(rule);
  }

  public void clearFaults() {
    if (!faultsEnabled()) throw new Problem(404, "NOT_FOUND", "찾을 수 없습니다.");
    faults.clear();
  }

  public List<Map<String, Object>> messages(String transactionId) {
    return log.find(transactionId);
  }

  private static boolean faultsEnabled() {
    return "true".equals(System.getenv("ENABLE_FAULTS"));
  }
}
