package com.sinhan.fep.contract;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContractTest {
  @Test
  void businessDayRollsOverAtCutoff() {
    var before = ZonedDateTime.of(2026, 11, 27, 23, 29, 59, 0, BusinessDay.ZONE).toInstant();
    var after = ZonedDateTime.of(2026, 11, 27, 23, 30, 0, 0, BusinessDay.ZONE).toInstant();
    assertEquals(LocalDate.of(2026, 11, 27), BusinessDay.of(before));
    assertEquals(LocalDate.of(2026, 11, 28), BusinessDay.of(after));
    assertEquals(LocalDate.of(2026, 11, 27), BusinessDay.closingAt(after));
  }

  @Test
  void envelopeRoundTripsTypedBody() {
    var e =
        Envelope.of(
            Header.request(MessageType.CARD_APPROVAL, "tx-1", Institution.CARD),
            new Bodies.CardApproval("tok", 18000, "가게"));
    var back = e.read(Bodies.CardApproval.class);
    assertEquals(18000, back.amountKrw());
    var reply = e.reply(ResponseCode.LIMIT_EXCEEDED);
    assertEquals(ResponseCode.Outcome.DECLINED, reply.header().response().outcome);
    assertEquals("tx-1", reply.header().transactionId());
  }

  @Test
  void masksCardNumbers() {
    assertEquals("9410-****-****-1234", Masking.cardNo("9410123456781234"));
    var masked = Masking.body(Map.of("memo", "card 9410-1234-5678-1234", "cvc", "123"));
    assertEquals("card 9410-****-****-1234", masked.get("memo"));
    assertEquals("****", masked.get("cvc"));
  }

  @Test
  void unknownResponseCodeIsTreatedAsUnknownOutcome() {
    assertEquals(ResponseCode.Outcome.UNKNOWN, ResponseCode.of("7777").outcome);
    assertEquals(ResponseCode.Outcome.UNKNOWN, ResponseCode.of(null).outcome);
  }
}
