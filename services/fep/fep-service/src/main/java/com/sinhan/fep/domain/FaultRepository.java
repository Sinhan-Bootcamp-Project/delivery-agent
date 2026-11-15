package com.sinhan.fep.domain;

import com.sinhan.fep.contract.Header;
import java.util.*;

public interface FaultRepository {
  void save(FaultRule rule);

  /** 헤더에 맞는 규칙을 1회 소비한다. 전문종별이 지정된 규칙을 먼저 쓴다. */
  Optional<FaultRule> take(Header header);

  void clear();
}
