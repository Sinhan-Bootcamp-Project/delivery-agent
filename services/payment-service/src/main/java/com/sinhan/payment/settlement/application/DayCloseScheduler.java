package com.sinhan.payment.settlement.application;

import com.sinhan.fep.contract.BusinessDay;
import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 매일 23:30(서울)에 그날 영업일을 마감한다. SETTLEMENT_SCHEDULE_ENABLED=false로 끌 수 있다. */
@Component
public class DayCloseScheduler {
  private final SettlementService settlement;
  private final Clock clock;

  public DayCloseScheduler(SettlementService settlement, Clock clock) {
    this.settlement = settlement;
    this.clock = clock;
  }

  @Scheduled(cron = "${settlement.cron:0 30 23 * * *}", zone = "Asia/Seoul")
  public void close() {
    if ("false".equals(System.getenv("SETTLEMENT_SCHEDULE_ENABLED"))) return;
    settlement.run(BusinessDay.closingAt(clock.instant()));
  }
}
