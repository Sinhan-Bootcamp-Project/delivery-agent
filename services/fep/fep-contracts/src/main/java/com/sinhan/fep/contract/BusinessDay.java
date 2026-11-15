package com.sinhan.fep.contract;

import java.time.*;

/** 영업일 규칙(REC-13). 서울 시간 23:30 이후 거래는 다음 영업일에 귀속. 모든 기관이 같은 기준을 쓴다. */
public final class BusinessDay {
  public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
  public static final LocalTime CUTOFF = LocalTime.of(23, 30);

  private BusinessDay() {}

  public static LocalDate of(Instant at) {
    var local = at.atZone(ZONE);
    return local.toLocalTime().isBefore(CUTOFF)
        ? local.toLocalDate()
        : local.toLocalDate().plusDays(1);
  }

  public static LocalDate today(Clock clock) {
    return of(clock.instant());
  }

  /** 23:30에 마감하는 영업일. 마감 시각에 실행하면 '오늘' 날짜를 돌려준다. */
  public static LocalDate closingAt(Instant at) {
    return of(at.minusSeconds(1));
  }
}
