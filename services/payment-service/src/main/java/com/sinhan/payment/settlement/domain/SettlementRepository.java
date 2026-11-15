package com.sinhan.payment.settlement.domain;

import java.time.LocalDate;
import java.util.*;

public interface SettlementRepository {
  /** 실행 시작. 이미 COMPLETED면 false. */
  boolean start(LocalDate date);

  Set<Step> completedSteps(LocalDate date);

  void stepDone(LocalDate date, Step step, String detail);

  void failed(LocalDate date, Step step, String error);

  void completed(LocalDate date, String status);

  void finding(LocalDate date, Step step, Reconciler.Finding f, String resolution);

  Map<String, Object> report(LocalDate date);
}
