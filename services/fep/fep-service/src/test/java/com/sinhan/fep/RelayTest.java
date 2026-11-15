package com.sinhan.fep;

import static org.junit.jupiter.api.Assertions.*;

import com.sinhan.fep.contract.*;
import com.sinhan.fep.domain.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RelayTest {
  final AtomicInteger delivered = new AtomicInteger();
  final Relay.Transport ok =
      (req, timeout) -> {
        delivered.incrementAndGet();
        return Optional.of(req.reply(ResponseCode.OK));
      };
  final Envelope request =
      Envelope.of(Header.request(MessageType.CARD_APPROVAL, "tx-1", Institution.CARD), Map.of());

  private FaultRule rule(FaultMode mode, long delay) {
    return new FaultRule(Institution.CARD, null, mode, delay, null, 1);
  }

  @Test
  void normalRelayReturnsInstitutionResponse() {
    var r = Relay.relay(request, null, ok, Duration.ofSeconds(3));
    assertEquals(ResponseCode.OK, r.response().header().response());
    assertEquals(1, delivered.get());
  }

  @Test
  void responseLostIsProcessedButReportedAsUnknown() {
    var r = Relay.relay(request, rule(FaultMode.RESPONSE_LOST, 0), ok, Duration.ofSeconds(3));
    assertEquals(ResponseCode.TIMEOUT, r.response().header().response());
    assertEquals(1, delivered.get());
  }

  @Test
  void noResponseNeverReachesInstitution() {
    var r = Relay.relay(request, rule(FaultMode.NO_RESPONSE, 0), ok, Duration.ofSeconds(3));
    assertEquals(ResponseCode.TIMEOUT, r.response().header().response());
    assertEquals(0, delivered.get());
  }

  @Test
  void delayBeyondTimeoutIsUnknown() {
    var r = Relay.relay(request, rule(FaultMode.DELAY, 20), ok, Duration.ofMillis(10));
    assertEquals(ResponseCode.TIMEOUT, r.response().header().response());
    assertEquals(1, delivered.get());
  }

  @Test
  void rejectUsesConfiguredCode() {
    var r = Relay.relay(request, rule(FaultMode.REJECT, 0), ok, Duration.ofSeconds(3));
    assertEquals(ResponseCode.LIMIT_EXCEEDED, r.response().header().response());
    assertEquals(0, delivered.get());
  }

  @Test
  void mismatchedTransactionIdIsUnknown() {
    Relay.Transport wrong =
        (req, t) ->
            Optional.of(
                Envelope.of(
                    new Header("CARD_APPROVAL", "other", "CRD", null, "0000"), Map.of()));
    var r = Relay.relay(request, null, wrong, Duration.ofSeconds(1));
    assertEquals(ResponseCode.TIMEOUT, r.response().header().response());
  }
}
