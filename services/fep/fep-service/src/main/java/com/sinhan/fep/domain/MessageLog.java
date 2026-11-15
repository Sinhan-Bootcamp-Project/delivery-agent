package com.sinhan.fep.domain;

import com.sinhan.fep.contract.Envelope;
import java.util.*;

/** 전문 로그(FEP-05). 바디는 마스킹 후 저장한다. */
public interface MessageLog {
  void request(Envelope e);

  void response(Envelope e, String fault, long elapsedMs);

  List<Map<String, Object>> find(String transactionId);
}
