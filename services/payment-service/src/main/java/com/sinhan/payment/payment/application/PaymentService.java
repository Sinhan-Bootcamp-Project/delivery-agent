package com.sinhan.payment.payment.application;

import com.sinhan.web.Canonical;
import com.sinhan.payment.shared.contract.Models.*;
import com.sinhan.fep.contract.BusinessDay;
import com.sinhan.payment.ledger.application.LedgerService;
import com.sinhan.payment.ledger.domain.Ledger.InsufficientBalance;
import com.sinhan.payment.outbox.application.Outbox;
import com.sinhan.payment.payment.domain.*;
import com.sinhan.payment.payment.domain.CardNetwork.Outcome;
import com.sinhan.payment.shared.Faults;
import com.sinhan.web.Problem;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 모의 결제(F-01, F-02, F-07).
 *
 * <ol>
 *   <li>승인 토큰 확인(control-service) → 같은 거래ID면 기존 결과(멱등, 내용이 다르면 409)
 *   <li>PENDING 커밋 → FEP → 카드사 (페이머니는 외부 호출 없이 원장만)
 *   <li>APPROVED + 원장 분개 + Outbox를 한 트랜잭션으로 저장
 *   <li>응답이 없으면 재전송하지 않고 상태 조회 → 그래도 모르면 UNKNOWN (복구 작업·대사가 확정)
 * </ol>
 *
 * 네트워크 호출은 DB 트랜잭션 밖에서 한다.
 */
@Service
public class PaymentService {
  static final Duration SETTLE_WINDOW = Duration.ofSeconds(30);
  private final PaymentRepository repo;
  private final CardNetwork cards;
  private final ApprovalCheck approvals;
  private final LedgerService ledger;
  private final Outbox outbox;
  private final TransactionTemplate tx;
  private final Faults faults;
  private final Clock clock;

  public PaymentService(
      PaymentRepository repo,
      CardNetwork cards,
      ApprovalCheck approvals,
      LedgerService ledger,
      Outbox outbox,
      TransactionTemplate tx,
      Faults faults,
      Clock clock) {
    this.repo = repo;
    this.cards = cards;
    this.approvals = approvals;
    this.ledger = ledger;
    this.outbox = outbox;
    this.tx = tx;
    this.faults = faults;
    this.clock = clock;
  }

  public PaymentResult charge(ExecutionCommand c) {
    if (!c.executionId().equals(c.proposal().executionId()))
      throw new Problem(409, "EXECUTION_MISMATCH", "실행 ID가 다릅니다.");
    var existing = repo.find(c.executionId());
    if (existing.isPresent()) return checked(existing.get(), c);
    approvals.verify(c);
    var p = c.proposal();
    var method = method(p.quote().paymentMethod());
    long amount = p.quote().chargeKrw();
    long discount = Math.max(0, p.quote().instantDiscountKrw());
    if (amount <= 0) throw new Problem(400, "INVALID_AMOUNT", "잘못된 금액입니다.");
    var draft =
        new Payment(
            c.executionId(), p.userId(), p.quote().paymentMethod(), method, amount, discount,
            Canonical.hash(p), PaymentStatus.PENDING, null, null, BusinessDay.of(clock.instant()), null,
            null);

    PaymentResult result =
        method.card ? chargeCard(draft, c, p.quote().basket().merchantName()) : chargePaymoney(draft, c);
    // 커밋은 됐는데 응답만 유실된 상황(시험용). 호출자는 조회로 확인해야 한다.
    if (faults.take("charge_response_lost"))
      throw new Problem(503, "SIMULATED_RESPONSE_LOSS", "결제 응답을 확인할 수 없습니다.");
    return result;
  }

  private PaymentResult chargePaymoney(Payment draft, ExecutionCommand c) {
    try {
      return tx.execute(
          s -> {
            repo.lock(draft.executionId());
            var again = repo.find(draft.executionId());
            if (again.isPresent()) return checked(again.get(), c);
            repo.insert(draft);
            ledger.payment(
                draft.executionId(),
                LedgerService.paymoneyAccount(draft.userId()),
                draft.amountKrw(),
                draft.discountKrw());
            repo.changeStatus(draft.executionId(), PaymentStatus.PENDING, PaymentStatus.APPROVED, null, null);
            outbox.publish(draft.executionId(), "PAYMENT_APPROVED", event(draft, "PAID"));
            return result(repo.find(draft.executionId()).orElseThrow());
          });
    } catch (InsufficientBalance e) {
      throw new Problem(409, "INSUFFICIENT_BALANCE", "페이머니 잔액이 부족합니다.");
    }
  }

  private PaymentResult chargeCard(Payment draft, ExecutionCommand c, String merchantName) {
    var token =
        repo.cardToken(draft.userId(), draft.method())
            .orElseThrow(() -> new Problem(409, "CARD_NOT_REGISTERED", "등록된 카드가 없습니다."));
    var created =
        tx.execute(
            s -> {
              repo.lock(draft.executionId());
              if (repo.find(draft.executionId()).isPresent()) return false;
              repo.insert(draft);
              return true;
            });
    if (!Boolean.TRUE.equals(created)) return checked(repo.find(draft.executionId()).orElseThrow(), c);

    var r = cards.approve(draft.executionId(), token, draft.amountKrw(), merchantName);
    if (r.outcome() == Outcome.UNKNOWN) r = cards.inquiry(draft.executionId());
    apply(draft.executionId(), r, false);
    var after = repo.find(draft.executionId()).orElseThrow();
    if (after.status() == PaymentStatus.FAILED) throw declined(after.failureCode());
    return result(after);
  }

  /**
   * 카드사 응답(또는 조회 결과)을 반영한다.
   *
   * @param settled true면 확정 시점(복구 작업·대사). NOT_FOUND를 실패로 확정한다. false면 아직 전송 중일 수 있어 UNKNOWN으로 둔다.
   */
  void apply(String id, CardNetwork.Result r, boolean settled) {
    tx.executeWithoutResult(
        s -> {
          repo.lock(id);
          var p = repo.find(id).orElseThrow();
          if (!p.status().unresolved()) return;
          switch (r.outcome()) {
            case SUCCESS -> {
              if (r.institutionStatus() != null && !r.institutionStatus().equals("APPROVED")) {
                // 카드사에는 이미 취소된 승인: 한끼 쪽은 승인 후 취소로 맞춘다.
                approve(p, r.approvalNo());
                cancelLocally(repo.find(id).orElseThrow());
              } else approve(p, r.approvalNo());
            }
            case DECLINED -> fail(p, r.code());
            case NOT_FOUND -> {
              if (settled) fail(p, "NOT_AT_INSTITUTION");
              else repo.changeStatus(id, p.status(), PaymentStatus.UNKNOWN, null, null);
            }
            case UNKNOWN -> {
              if (p.status() == PaymentStatus.PENDING)
                repo.changeStatus(id, PaymentStatus.PENDING, PaymentStatus.UNKNOWN, null, null);
            }
          }
        });
  }

  private void approve(Payment p, String approvalNo) {
    p.moveTo(PaymentStatus.APPROVED);
    repo.changeStatus(p.executionId(), p.status(), PaymentStatus.APPROVED, approvalNo, null);
    ledger.payment(p.executionId(), LedgerService.cardAccount(), p.amountKrw(), p.discountKrw());
    outbox.publish(p.executionId(), "PAYMENT_APPROVED", event(p, "PAID"));
  }

  private void fail(Payment p, String code) {
    p.moveTo(PaymentStatus.FAILED);
    repo.changeStatus(p.executionId(), p.status(), PaymentStatus.FAILED, null, code);
    outbox.publish(p.executionId(), "PAYMENT_FAILED", event(p, "FAILED"));
  }

  /** 상태 조회로 미확정 건을 확정한다(복구 작업). 마지막 변경 후 SETTLE_WINDOW가 지난 건만 NOT_FOUND를 실패로 본다. */
  public void resolve(String id) {
    var p = repo.find(id).orElse(null);
    if (p == null || !p.status().unresolved() || !p.method().card) return;
    boolean settled = p.updatedAt() == null || p.updatedAt().isBefore(clock.instant().minus(SETTLE_WINDOW));
    apply(id, cards.inquiry(id), settled);
  }

  /** 대사 결과로 확정(F-08). 기관 내역에 승인이 있으면 승인, 없으면 실패. */
  public void settleFromReconciliation(String id, boolean approvedAtInstitution, String approvalNo) {
    apply(
        id,
        approvedAtInstitution
            ? new CardNetwork.Result(Outcome.SUCCESS, "0000", approvalNo, "APPROVED")
            : new CardNetwork.Result(Outcome.NOT_FOUND, "4040", null, null),
        true);
  }

  public PaymentResult cancel(String id) {
    if (faults.take("refund_unavailable"))
      throw new Problem(503, "REFUND_UNAVAILABLE", "환불 결과를 확인하고 있습니다.");
    var p = required(id);
    if (p.status() == PaymentStatus.APPROVED || p.status() == PaymentStatus.CANCEL_PENDING) {
      if (p.method().card) cancelCard(p);
      else
        tx.executeWithoutResult(
            s -> {
              repo.lock(id);
              var current = repo.find(id).orElseThrow();
              if (current.status() == PaymentStatus.APPROVED) cancelLocally(current);
            });
    }
    var result = result(required(id));
    if (faults.take("refund_response_lost"))
      throw new Problem(503, "SIMULATED_RESPONSE_LOSS", "환불 응답을 확인할 수 없습니다.");
    return result;
  }

  private void cancelCard(Payment p) {
    String id = p.executionId();
    if (p.status() == PaymentStatus.APPROVED)
      tx.executeWithoutResult(
          s -> {
            repo.lock(id);
            if (repo.find(id).orElseThrow().status() == PaymentStatus.APPROVED)
              repo.changeStatus(id, PaymentStatus.APPROVED, PaymentStatus.CANCEL_PENDING, null, null);
          });
    // 카드 취소는 카드사에서 원거래ID로 멱등이므로 나중 호출에서 다시 보내도 이중 취소가 되지 않는다.
    var r = cards.cancel(id, p.amountKrw());
    if (r.outcome() == Outcome.UNKNOWN) {
      var q = cards.inquiry(id);
      if (q.outcome() == Outcome.SUCCESS && "CANCELLED".equals(q.institutionStatus())) r = q;
      else throw new Problem(503, "REFUND_UNKNOWN", "카드 취소 결과를 확인하고 있습니다.");
    }
    if (r.outcome() != Outcome.SUCCESS) {
      String code = r.code();
      tx.executeWithoutResult(
          s -> repo.changeStatus(id, PaymentStatus.CANCEL_PENDING, PaymentStatus.APPROVED, null, code));
      throw new Problem(409, "CANCEL_REJECTED", "카드사가 취소를 거절했습니다.");
    }
    tx.executeWithoutResult(
        s -> {
          repo.lock(id);
          var current = repo.find(id).orElseThrow();
          if (current.status() == PaymentStatus.CANCEL_PENDING) cancelLocally(current);
        });
  }

  /** 역분개 + CANCELLED + Outbox. 호출자 트랜잭션 안. */
  private void cancelLocally(Payment p) {
    p.moveTo(PaymentStatus.CANCELLED);
    ledger.cancel(p.executionId());
    repo.markCancelled(p.executionId(), p.status(), BusinessDay.of(clock.instant()));
    outbox.publish(p.executionId(), "PAYMENT_CANCELLED", event(p, "REFUNDED"));
  }

  public PaymentResult lookup(String id) {
    return result(required(id));
  }

  public List<String> staleIds(Collection<PaymentStatus> statuses) {
    return repo.stale(statuses, clock.instant().minus(SETTLE_WINDOW));
  }

  /** 결제수단별 남은 한도(카드). 조회 실패한 수단은 빠진다. 혜택 서비스의 후보 필터에 쓴다. */
  public Map<String, Long> cardLimits(String userId) {
    var limits = new LinkedHashMap<String, Long>();
    for (var m : List.of(PaymentMethod.CARD_A, PaymentMethod.CARD_B))
      repo.cardToken(userId, m)
          .map(cards::remainingLimit)
          .filter(v -> v >= 0)
          .ifPresent(v -> limits.put(m.name(), v));
    return limits;
  }

  public List<Payment> cardPaymentsTouching(LocalDate date) {
    return repo.cardPaymentsTouching(date);
  }

  private Payment required(String id) {
    return repo.find(id).orElseThrow(() -> new Problem(404, "PAYMENT_NOT_FOUND", "거래 내역이 없습니다."));
  }

  private PaymentResult checked(Payment p, ExecutionCommand c) {
    if (!p.digest().equals(Canonical.hash(c.proposal())))
      throw new Problem(409, "IDEMPOTENCY_CONFLICT", "같은 실행 ID에 다른 결제 요청입니다.");
    if (p.status() == PaymentStatus.FAILED) throw declined(p.failureCode());
    return result(p);
  }

  private static Problem declined(String code) {
    // 한도·잔액 부족은 주문 서비스가 새 주문안을 만들도록 INSUFFICIENT_BALANCE로 알린다.
    if ("1001".equals(code) || "1002".equals(code))
      return new Problem(409, "INSUFFICIENT_BALANCE", "카드 한도 또는 잔액이 부족합니다.");
    return new Problem(409, "PAYMENT_DECLINED", "결제가 거절되었습니다.");
  }

  private static PaymentMethod method(String code) {
    try {
      return PaymentMethod.from(code);
    } catch (IllegalArgumentException e) {
      throw new Problem(400, "UNSUPPORTED_METHOD", "지원하지 않는 결제수단입니다.");
    }
  }

  private static Map<String, Object> event(Payment p, String status) {
    return Map.of(
        "executionId", p.executionId(),
        "userId", p.userId(),
        "status", status,
        "amountKrw", p.amountKrw(),
        "paymentMethod", p.requestedMethod());
  }

  private static PaymentResult result(Payment p) {
    return new PaymentResult(
        p.executionId(), p.status().external(), Math.toIntExact(p.amountKrw()), p.requestedMethod());
  }
}
