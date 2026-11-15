package com.sinhan.institution.kftc.domain;

import com.sinhan.fep.contract.Bodies.NetEntry;
import java.util.*;

/** 차액 산출(VFI-03). 기관 간 이체만 반영한다(당행 이체는 차액 0). 결과 합계는 항상 0이어야 한다. */
public final class NetCalculator {
  private NetCalculator() {}

  public record Movement(String from, String to, long amountKrw) {}

  public static List<NetEntry> net(List<Movement> movements) {
    var net = new TreeMap<String, Long>();
    for (var m : movements) {
      if (m.from().equals(m.to())) continue;
      net.merge(m.from(), -m.amountKrw(), Long::sum);
      net.merge(m.to(), m.amountKrw(), Long::sum);
    }
    var entries = net.entrySet().stream().map(e -> new NetEntry(e.getKey(), e.getValue())).toList();
    if (entries.stream().mapToLong(NetEntry::netKrw).sum() != 0)
      throw new IllegalStateException("net settlement does not balance");
    return entries;
  }
}
