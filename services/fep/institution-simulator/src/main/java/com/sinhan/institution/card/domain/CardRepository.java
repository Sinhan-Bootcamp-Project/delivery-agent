package com.sinhan.institution.card.domain;

import com.sinhan.fep.contract.Bodies.DailyRecord;
import java.time.LocalDate;
import java.util.*;

public interface CardRepository {
  Optional<Card> lockCard(String token);

  Optional<Authorization> authorization(String transactionId);

  void approve(Authorization a);

  void cancel(String transactionId, LocalDate businessDate);

  void changeUsed(String token, long delta);

  List<DailyRecord> daily(LocalDate businessDate);

  long payoutAmount(LocalDate businessDate);

  /** 영업일별 카드대금 지급 기록. 이미 있으면 기존 금액. */
  Optional<Long> payout(LocalDate businessDate);

  void recordPayout(LocalDate businessDate, long amountKrw, String transferStatus);

  void recordSettlementLeg(String legId, long amountKrw);
}
