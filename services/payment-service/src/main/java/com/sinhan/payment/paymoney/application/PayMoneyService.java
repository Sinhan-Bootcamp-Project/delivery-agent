package com.sinhan.payment.paymoney.application;

import com.sinhan.fep.contract.BusinessDay;
import com.sinhan.payment.ledger.application.LedgerService;
import com.sinhan.payment.outbox.application.Outbox;
import com.sinhan.payment.paymoney.domain.*;
import com.sinhan.payment.paymoney.domain.TransferNetwork.Outcome;
import com.sinhan.web.Problem;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 페이머니 충전·잔액(F-03). 충전은 결제원 이체가 완료된 뒤에만 원장에 기장한다. 결제원이 입금 구간 실패를 역이체로 보상하므로
 * 한끼는 COMPLETED일 때만 페이머니를 늘린다.
 */
@Service
public class PayMoneyService {
  static final Duration SETTLE_WINDOW = Duration.ofSeconds(30);
  private final TopUpRepository repo;
  private final TransferNetwork network;
  private final LedgerService ledger;
  private final Outbox outbox;
  private final TransactionTemplate tx;
  private final Clock clock;

  public PayMoneyService(
      TopUpRepository repo,
      TransferNetwork network,
      LedgerService ledger,
      Outbox outbox,
      TransactionTemplate tx,
      Clock clock) {
    this.repo = repo;
    this.network = network;
    this.ledger = ledger;
    this.outbox = outbox;
    this.tx = tx;
    this.clock = clock;
  }

  public record TopUpView(String id, String status, long amountKrw, long balanceKrw, String failureCode) {}

  public TopUpView topUp(String userId, String idempotencyKey, long amountKrw) {
    if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9-]{8,64}"))
      throw new Problem(400, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key 헤더가 필요합니다.");
    if (amountKrw < TopUp.MIN_KRW || amountKrw > TopUp.MAX_KRW)
      throw new Problem(400, "INVALID_AMOUNT", "충전 금액은 1천원 이상 100만원 이하입니다.");
    String id =
        "TU-" + UUID.nameUUIDFromBytes((userId + ":" + idempotencyKey).getBytes(StandardCharsets.UTF_8));
    var existing = repo.find(id);
    if (existing.isPresent()) {
      if (existing.get().amountKrw() != amountKrw)
        throw new Problem(409, "IDEMPOTENCY_CONFLICT", "같은 키에 다른 충전 요청입니다.");
      return view(existing.get());
    }
    String account =
        repo.linkedAccount(userId)
            .orElseThrow(() -> new Problem(409, "ACCOUNT_NOT_LINKED", "연결된 은행 계좌가 없습니다."));
    var created =
        tx.execute(
            s -> {
              repo.lock(id);
              if (repo.find(id).isPresent()) return false;
              if (ledger.paymoneyBalance(userId) + amountKrw > TopUp.HOLDING_LIMIT_KRW)
                throw new Problem(409, "HOLDING_LIMIT_EXCEEDED", "페이머니 보유 한도(200만원)를 넘습니다.");
              repo.insert(
                  new TopUp(id, userId, account, amountKrw, TopUp.REQUESTED, null, BusinessDay.of(clock.instant()), null));
              return true;
            });
    if (Boolean.TRUE.equals(created)) {
      var r = network.topUp(id, account, amountKrw);
      if (r.outcome() == Outcome.UNKNOWN) r = network.inquiry(id);
      apply(id, r, false);
    }
    return view(repo.find(id).orElseThrow());
  }

  void apply(String id, TransferNetwork.Result r, boolean settled) {
    tx.executeWithoutResult(
        s -> {
          repo.lock(id);
          var t = repo.find(id).orElseThrow();
          if (!t.unresolved()) return;
          switch (r.outcome()) {
            case COMPLETED -> {
              repo.changeStatus(id, t.status(), TopUp.COMPLETED, null);
              ledger.topUp(id, t.userId(), t.amountKrw());
              outbox.publish(id, "PAYMONEY_TOPPED_UP", Map.of("userId", t.userId(), "amountKrw", t.amountKrw()));
            }
            case FAILED -> repo.changeStatus(id, t.status(), TopUp.FAILED, r.code());
            case NOT_FOUND -> {
              if (settled) repo.changeStatus(id, t.status(), TopUp.FAILED, "NOT_AT_INSTITUTION");
              else if (!t.status().equals(TopUp.UNKNOWN)) repo.changeStatus(id, t.status(), TopUp.UNKNOWN, null);
            }
            case UNKNOWN -> {
              if (!t.status().equals(TopUp.UNKNOWN)) repo.changeStatus(id, t.status(), TopUp.UNKNOWN, null);
            }
          }
        });
  }

  public void resolve(String id) {
    var t = repo.find(id).orElse(null);
    if (t == null || !t.unresolved()) return;
    boolean settled = t.updatedAt() == null || t.updatedAt().isBefore(clock.instant().minus(SETTLE_WINDOW));
    apply(id, network.inquiry(id), settled);
  }

  /** 대사 결과로 확정. institutionStatus = 결제원 이체 상태(COMPLETED/FAILED/COMPENSATED) 또는 null(내역 없음). */
  public void settleFromReconciliation(String id, String institutionStatus) {
    var outcome =
        institutionStatus == null
            ? Outcome.NOT_FOUND
            : institutionStatus.equals("COMPLETED")
                ? Outcome.COMPLETED
                : institutionStatus.equals("FAILED") || institutionStatus.equals("COMPENSATED")
                    ? Outcome.FAILED
                    : Outcome.UNKNOWN;
    apply(id, new TransferNetwork.Result(outcome, null), true);
  }

  public TopUpView find(String userId, String id) {
    var t = repo.find(id).filter(x -> x.userId().equals(userId));
    return view(t.orElseThrow(() -> new Problem(404, "TOPUP_NOT_FOUND", "충전 내역이 없습니다.")));
  }

  public long balance(String userId) {
    return tx.execute(s -> ledger.paymoneyBalance(userId));
  }

  public long totalBalance() {
    return tx.execute(s -> ledger.totalPaymoney());
  }

  public OptionalLong trustBalance() {
    return network.trustBalance();
  }

  public List<String> staleIds() {
    return repo.stale(clock.instant().minus(SETTLE_WINDOW));
  }

  public List<TopUp> byBusinessDate(LocalDate date) {
    return repo.byBusinessDate(date);
  }

  private TopUpView view(TopUp t) {
    return new TopUpView(t.id(), t.status(), t.amountKrw(), balance(t.userId()), t.failureCode());
  }
}
