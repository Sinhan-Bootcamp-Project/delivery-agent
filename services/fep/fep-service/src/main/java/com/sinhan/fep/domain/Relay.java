package com.sinhan.fep.domain;

import com.sinhan.fep.contract.*;
import java.time.Duration;
import java.util.Optional;

/**
 * 중계 규칙. 재전송하지 않는다: 기관 응답을 받지 못하면 TIMEOUT(모름)을 돌려주고, 호출자가 같은 거래ID로 상태 조회를 보내야 한다.
 */
public final class Relay {
  private Relay() {}

  /** 기관 호출 포트. 응답이 없으면 empty. */
  public interface Transport {
    Optional<Envelope> send(Envelope request, Duration timeout);
  }

  public record Outcome(Envelope response, String fault) {}

  public static Outcome relay(Envelope request, FaultRule fault, Transport transport, Duration timeout) {
    if (fault == null) return new Outcome(forward(request, transport, timeout), null);
    String tag = fault.mode().name();
    return switch (fault.mode()) {
      case REJECT -> new Outcome(request.reply(ResponseCode.of(fault.rejectCode())), tag);
      case NO_RESPONSE -> new Outcome(request.reply(ResponseCode.TIMEOUT), tag);
      case RESPONSE_LOST -> {
        forward(request, transport, timeout);
        yield new Outcome(request.reply(ResponseCode.TIMEOUT), tag);
      }
      case DELAY -> {
        sleep(fault.delayMs());
        var r = forward(request, transport, timeout);
        yield new Outcome(
            Duration.ofMillis(fault.delayMs()).compareTo(timeout) >= 0
                ? request.reply(ResponseCode.TIMEOUT)
                : r,
            tag);
      }
    };
  }

  private static Envelope forward(Envelope request, Transport transport, Duration timeout) {
    return transport
        .send(request, timeout)
        .filter(r -> r.header() != null && request.header().transactionId().equals(r.header().transactionId()))
        .orElseGet(() -> request.reply(ResponseCode.TIMEOUT));
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
