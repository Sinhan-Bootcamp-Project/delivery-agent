package com.sinhan.institution.kftc.domain;

import com.sinhan.fep.contract.Bodies.*;
import java.time.LocalDate;
import java.util.*;

public interface TransferRepository {
  Optional<TransferOrder> find(String transactionId);

  /** 없으면 REQUESTED로 만든다. 이미 있으면 그대로 둔다. */
  void createIfAbsent(String transactionId, Transfer request, LocalDate businessDate);

  void update(String transactionId, String status, String leg, String legStatus, String failureCode);

  List<NetCalculator.Movement> completedMovements(LocalDate businessDate);

  List<DailyRecord> daily(LocalDate businessDate);

  Optional<String> closingStatus(LocalDate businessDate);

  List<NetEntry> closingEntries(LocalDate businessDate);

  void saveClosing(LocalDate businessDate, List<NetEntry> entries, String status);
}
