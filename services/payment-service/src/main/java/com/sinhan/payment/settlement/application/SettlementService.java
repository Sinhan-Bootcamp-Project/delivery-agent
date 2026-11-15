package com.sinhan.payment.settlement.application;

import com.sinhan.fep.contract.*;
import com.sinhan.fep.contract.Bodies.*;
import com.sinhan.payment.ledger.application.LedgerService;
import com.sinhan.payment.payment.application.PaymentService;
import com.sinhan.payment.payment.domain.PaymentStatus;
import com.sinhan.payment.paymoney.application.PayMoneyService;
import com.sinhan.payment.paymoney.domain.TopUp;
import com.sinhan.payment.settlement.domain.*;
import com.sinhan.payment.settlement.domain.Reconciler.*;
import com.sinhan.payment.shared.FepGateway;
import com.sinhan.web.Problem;
import java.time.LocalDate;
import java.util.*;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 일 마감·대사 오케스트레이션(F-08). 단계 순서는 {@link Step} 참고. */
@Service
public class SettlementService {
  private static final Logger LOG = LoggerFactory.getLogger(SettlementService.class);
  private final SettlementRepository repo;
  private final FepGateway fep;
  private final PaymentService payments;
  private final PayMoneyService paymoney;
  private final LedgerService ledger;
  private final TransactionTemplate tx;

  public SettlementService(
      SettlementRepository repo,
      FepGateway fep,
      PaymentService payments,
      PayMoneyService paymoney,
      LedgerService ledger,
      TransactionTemplate tx) {
    this.repo = repo;
    this.fep = fep;
    this.payments = payments;
    this.paymoney = paymoney;
    this.ledger = ledger;
    this.tx = tx;
  }

  public synchronized Map<String, Object> run(LocalDate date) {
    if (!repo.start(date)) return repo.report(date);
    var done = repo.completedSteps(date);
    int mismatches = 0;
    for (var step : Step.values()) {
      if (done.contains(step)) continue;
      try {
        mismatches += execute(date, step);
      } catch (RuntimeException e) {
        LOG.error("settlement date={} step={} failed errorType={}", date, step, e.getClass().getSimpleName());
        repo.failed(date, step, e.getMessage());
        return repo.report(date);
      }
    }
    repo.completed(date, "COMPLETED");
    return repo.report(date);
  }

  public Map<String, Object> report(LocalDate date) {
    return repo.report(date);
  }

  private int execute(LocalDate date, Step step) {
    return switch (step) {
      case CARD_CLOSE -> {
        var r = expectOk(fep.send(MessageType.DAY_CLOSE, "CLOSE-CRD-" + date, Institution.CARD, new DayClose(date)));
        var body = r.read(DayCloseResult.class);
        repo.stepDone(date, step, "payout=" + body.amountKrw() + " status=" + body.status());
        yield 0;
      }
      case KFTC_CLOSE -> {
        var r = expectOk(fep.send(MessageType.DAY_CLOSE, "CLOSE-KFT-" + date, Institution.KFTC, new DayClose(date)));
        repo.stepDone(date, step, "net=" + r.read(DayCloseResult.class).netEntries());
        yield 0;
      }
      case BOK_CHECK -> {
        var kftc = daily(Institution.KFTC, date); // 결제원 내역에서 기관 간 이체 차액을 다시 계산하지 않고 BOK 기록과 비교한다
        var bok = daily(Institution.BOK, date);
        long sum = bok.stream().mapToLong(DailyRecord::amountKrw).sum();
        int n = 0;
        if (sum != 0) {
          repo.finding(
              date, step, new Finding(Kind.AMOUNT_MISMATCH, "NET-" + date, "NET", null, null), "BOK 차액 합계 " + sum);
          n++;
        }
        repo.stepDone(date, step, "bokEntries=" + bok.size() + " kftcTransfers=" + kftc.size());
        yield n;
      }
      case RECONCILE_CARD -> reconcileCard(date);
      case RECONCILE_TOPUP -> reconcileTopUp(date);
      case CARD_PAYOUT -> cardPayout(date);
      case TRUST_CHECK -> {
        long total = paymoney.totalBalance();
        var trust = paymoney.trustBalance();
        if (trust.isEmpty()) throw new IllegalStateException("trust balance unavailable");
        int n = 0;
        if (trust.getAsLong() != total) {
          repo.finding(
              date,
              step,
              new Finding(Kind.AMOUNT_MISMATCH, "TRUST-" + date, "TRUST", new Item("TRUST", "TRUST", total, "HANKI"), new Item("TRUST", "TRUST", trust.getAsLong(), "BANK")),
              "충전금 불일치: 페이머니 합 " + total + " / 충전금 계좌 " + trust.getAsLong());
          n++;
        }
        repo.stepDone(date, step, "paymoney=" + total + " trust=" + trust.getAsLong());
        yield n;
      }
      case LEDGER_CHECK -> {
        var drift = tx.execute(s -> ledger.driftedAccounts());
        long imbalance = tx.execute(s -> ledger.imbalance());
        int n = 0;
        for (var account : drift) {
          repo.finding(date, step, new Finding(Kind.AMOUNT_MISMATCH, account, "LEDGER", null, null), "캐시 잔액 ≠ 분개 합");
          n++;
        }
        if (imbalance != 0) {
          repo.finding(date, step, new Finding(Kind.AMOUNT_MISMATCH, "JOURNAL", "LEDGER", null, null), "차변-대변 " + imbalance);
          n++;
        }
        repo.stepDone(date, step, "drifted=" + drift.size() + " imbalance=" + imbalance);
        yield n;
      }
    };
  }

  private int reconcileCard(LocalDate date) {
    List<Item> local = new ArrayList<>();
    for (var p : payments.cardPaymentsTouching(date)) {
      if (p.businessDate().equals(date)) {
        String status =
            switch (p.status()) {
              case APPROVED, CANCEL_PENDING -> "APPROVED";
              case CANCELLED -> "CANCELLED";
              case FAILED -> Reconciler.FAILED;
              case PENDING, UNKNOWN -> Reconciler.UNKNOWN;
            };
        local.add(new Item(p.executionId(), "APPROVAL", p.amountKrw(), status));
      }
      if (date.equals(p.cancelBusinessDate()))
        local.add(new Item(p.executionId(), "CANCEL", p.amountKrw(), "CANCELLED"));
    }
    var remote =
        daily(Institution.CARD, date).stream()
            .map(r -> new Item(r.transactionId(), r.type(), r.amountKrw(), r.type().equals("CANCEL") ? "CANCELLED" : r.status()))
            .toList();
    int n = 0;
    for (var f : Reconciler.compare(local, remote)) {
      String resolution = null;
      if (f.kind() == Kind.RESOLVE_UNKNOWN) {
        payments.settleFromReconciliation(f.transactionId(), f.remote() != null, null);
        resolution = f.remote() == null ? "FAILED로 확정(카드사 내역 없음)" : "APPROVED로 확정";
      } else n++;
      repo.finding(date, Step.RECONCILE_CARD, f, resolution);
    }
    repo.stepDone(date, Step.RECONCILE_CARD, "local=" + local.size() + " remote=" + remote.size() + " mismatches=" + n);
    return n;
  }

  private int reconcileTopUp(LocalDate date) {
    var local =
        paymoney.byBusinessDate(date).stream()
            .map(t -> new Item(t.id(), "TOPUP", t.amountKrw(), t.unresolved() ? Reconciler.UNKNOWN : t.status()))
            .toList();
    var remoteRaw =
        daily(Institution.KFTC, date).stream().filter(r -> r.type().equals("TOPUP")).toList();
    var remote =
        remoteRaw.stream()
            .map(r -> new Item(r.transactionId(), "TOPUP", r.amountKrw(), r.status().equals("COMPENSATED") ? TopUp.FAILED : r.status()))
            .toList();
    int n = 0;
    for (var f : Reconciler.compare(local, remote)) {
      String resolution = null;
      if (f.kind() == Kind.RESOLVE_UNKNOWN) {
        String status = remoteRaw.stream().filter(r -> r.transactionId().equals(f.transactionId())).map(DailyRecord::status).findFirst().orElse(null);
        paymoney.settleFromReconciliation(f.transactionId(), status);
        resolution = "결제원 상태 " + (status == null ? "없음" : status) + "로 확정";
      } else n++;
      repo.finding(date, Step.RECONCILE_TOPUP, f, resolution);
    }
    repo.stepDone(date, Step.RECONCILE_TOPUP, "local=" + local.size() + " remote=" + remote.size() + " mismatches=" + n);
    return n;
  }

  private int cardPayout(LocalDate date) {
    String id = "CARDPAYOUT-" + date;
    long expected = 0;
    for (var p : payments.cardPaymentsTouching(date)) {
      if (p.businessDate().equals(date)
          && EnumSet.of(PaymentStatus.APPROVED, PaymentStatus.CANCEL_PENDING, PaymentStatus.CANCELLED).contains(p.status()))
        expected += p.amountKrw();
      if (date.equals(p.cancelBusinessDate())) expected -= p.amountKrw();
    }
    var transfer =
        daily(Institution.KFTC, date).stream().filter(r -> r.transactionId().equals(id)).findFirst();
    long paid = transfer.filter(r -> r.status().equals("COMPLETED")).map(DailyRecord::amountKrw).orElse(0L);
    int n = 0;
    if (paid != Math.max(0, expected)) {
      repo.finding(
          date,
          Step.CARD_PAYOUT,
          new Finding(Kind.AMOUNT_MISMATCH, id, "CARD_PAYOUT", new Item(id, "CARD_PAYOUT", expected, "EXPECTED"), new Item(id, "CARD_PAYOUT", paid, "PAID")),
          null);
      n++;
    }
    if (paid > 0) tx.executeWithoutResult(s -> ledger.cardPayout(id, paid));
    repo.stepDone(date, Step.CARD_PAYOUT, "expected=" + expected + " paid=" + paid);
    return n;
  }

  private List<DailyRecord> daily(Institution institution, LocalDate date) {
    var r =
        expectOk(
            fep.send(
                MessageType.DAILY_TRANSACTIONS,
                "DAILY-" + institution.code + "-" + date + "-" + UUID.randomUUID().toString().substring(0, 8),
                institution,
                new DailyQuery(date)));
    return r.read(DailyTransactions.class).records();
  }

  private static Envelope expectOk(Envelope r) {
    if (r.header().response() != ResponseCode.OK)
      throw new Problem(503, "SETTLEMENT_STEP_FAILED", r.header().institutionCode() + " " + r.header().messageType() + " " + r.header().responseCode());
    return r;
  }
}
