package com.sinhan.institution.bok.domain;

import com.sinhan.fep.contract.Bodies.NetEntry;
import java.time.LocalDate;
import java.util.List;

/** 한국은행 차액결제 배치(VFI-04). 영업일당 1건. 합계가 0이 아니면 받지 않는다. */
public record SettlementBatch(LocalDate businessDate, List<NetEntry> entries) {
  public SettlementBatch {
    entries = List.copyOf(entries);
    if (entries.stream().mapToLong(NetEntry::netKrw).sum() != 0)
      throw new IllegalArgumentException("net entries must sum to zero");
  }

  public boolean sameAs(List<NetEntry> other) {
    return new java.util.HashSet<>(entries).equals(new java.util.HashSet<>(other))
        && entries.size() == other.size();
  }
}
