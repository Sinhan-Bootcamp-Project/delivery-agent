package com.sinhan.payment.paymoney.domain;

import java.time.*;
import java.util.*;

public interface TopUpRepository {
  void lock(String id);

  Optional<TopUp> find(String id);

  void insert(TopUp t);

  void changeStatus(String id, String from, String to, String failureCode);

  List<String> stale(Instant before);

  List<TopUp> byBusinessDate(LocalDate date);

  Optional<String> linkedAccount(String userId);
}
