package com.sinhan.institution.kftc.application;

import com.sinhan.fep.contract.*;
import com.sinhan.fep.contract.Bodies.*;
import com.sinhan.institution.kftc.domain.*;
import com.sinhan.institution.shared.*;
import java.time.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 가상 금융결제원(VFI-03). 이체 중계(출금 기관 → 입금 기관)와 23:30 마감 후 차액 산출·한국은행 전송.
 *
 * <p>2PC를 쓰지 않는다. 구간마다 상태를 커밋하고, 입금 구간이 실패하면 출금 구간을 역이체로 보상한다(Saga, REC-09). 네트워크 호출은
 * DB 트랜잭션 밖에서 한다. 같은 거래ID로 다시 오면 멈춘 구간부터 이어서 처리한다.
 */
@Service
public class KftcService implements MessageHandler {
  private final TransferRepository repo;
  private final TransactionTemplate tx;
  private final InterbankNetwork network;
  private final Clock clock;

  public KftcService(
      TransferRepository repo,
      @Qualifier("kftcTx") TransactionTemplate tx,
      InterbankNetwork network,
      Clock clock) {
    this.repo = repo;
    this.tx = tx;
    this.network = network;
    this.clock = clock;
  }

  @Override
  public Institution institution() {
    return Institution.KFTC;
  }

  @Override
  public Envelope handle(Envelope m, Channel channel) {
    return switch (m.header().type()) {
      case TRANSFER -> transfer(m);
      case TRANSACTION_INQUIRY ->
          repo.find(m.header().transactionId())
              .map(
                  t ->
                      m.reply(
                          ResponseCode.OK,
                          new InquiryResult(t.status(), t.request().amountKrw(), t.failureCode())))
              .orElseGet(() -> m.reply(ResponseCode.NOT_FOUND));
      case DAILY_TRANSACTIONS -> {
        var date = m.read(DailyQuery.class).businessDate();
        yield m.reply(ResponseCode.OK, new DailyTransactions(date, repo.daily(date)));
      }
      case DAY_CLOSE -> channel == Channel.FEP ? close(m) : m.reply(ResponseCode.NOT_ALLOWED);
      default -> m.reply(ResponseCode.NOT_ALLOWED);
    };
  }

  private Envelope transfer(Envelope m) {
    var request = m.read(Transfer.class);
    String id = m.header().transactionId();
    if (request.amountKrw() <= 0) return m.reply(ResponseCode.INVALID_STATE);
    tx.executeWithoutResult(s -> repo.createIfAbsent(id, request, BusinessDay.today(clock)));
    var t = repo.find(id).orElseThrow();
    if (!t.request().equals(request)) return m.reply(ResponseCode.DUPLICATE_CONFLICT);

    if (t.status().equals(TransferOrder.REQUESTED)) {
      var r = posting(MessageType.WITHDRAW, request.fromInstitution(), t.debitLeg(), request.fromAccount(), request);
      var code = r.header().response();
      if (code == ResponseCode.OK) update(id, TransferOrder.DEBITED, "debit", "OK", null);
      else if (code.outcome == ResponseCode.Outcome.DECLINED)
        update(id, TransferOrder.FAILED, "debit", "DECLINED", code.code);
      else return m.reply(ResponseCode.SYSTEM_ERROR, t.result());
      t = repo.find(id).orElseThrow();
    }
    if (t.status().equals(TransferOrder.DEBITED) && !"FAILED".equals(t.creditStatus())) {
      var r = posting(MessageType.DEPOSIT, request.toInstitution(), t.creditLeg(), request.toAccount(), request);
      if (r.header().response() == ResponseCode.OK)
        update(id, TransferOrder.COMPLETED, "credit", "OK", null);
      else update(id, TransferOrder.DEBITED, "credit", "FAILED", r.header().responseCode());
      t = repo.find(id).orElseThrow();
    }
    if (t.status().equals(TransferOrder.DEBITED)) {
      // 입금 구간 실패가 확정된 건만 여기로 온다. 입금을 다시 시도하지 않고 출금을 되돌린다.
      var back =
          posting(
              MessageType.DEPOSIT, request.fromInstitution(), t.compensationLeg(), request.fromAccount(), request);
      if (back.header().response() != ResponseCode.OK)
        return m.reply(ResponseCode.SYSTEM_ERROR, t.result());
      update(id, TransferOrder.COMPENSATED, "compensation", "OK", null);
      t = repo.find(id).orElseThrow();
    }
    var code =
        switch (t.status()) {
          case TransferOrder.COMPLETED -> ResponseCode.OK;
          case TransferOrder.FAILED, TransferOrder.COMPENSATED -> declined(t.failureCode());
          default -> ResponseCode.SYSTEM_ERROR;
        };
    return m.reply(code, t.result());
  }

  private static ResponseCode declined(String code) {
    var r = ResponseCode.of(code);
    return r.outcome == ResponseCode.Outcome.DECLINED ? r : ResponseCode.INVALID_STATE;
  }

  private Envelope posting(
      MessageType type, String institution, String legId, String account, Transfer t) {
    return network.send(
        Envelope.of(
            Header.request(type, legId, Institution.of(institution)),
            new AccountPosting(legId, account, t.amountKrw(), t.purpose())));
  }

  private void update(String id, String status, String leg, String legStatus, String failure) {
    tx.executeWithoutResult(s -> repo.update(id, status, leg, legStatus, failure));
  }

  /** 23:30 마감: 당일 완료 이체로 기관별 차액을 산출하고 한국은행에 차액결제 전문을 보낸다. 영업일당 1회(멱등). */
  private Envelope close(Envelope m) {
    LocalDate date = m.read(DayClose.class).businessDate();
    if (!date.isBefore(BusinessDay.today(clock)) && !DemoMode.earlyCloseAllowed()) return m.reply(ResponseCode.INVALID_STATE);
    var status = repo.closingStatus(date);
    if (status.isEmpty()) {
      var entries = NetCalculator.net(repo.completedMovements(date));
      tx.executeWithoutResult(s -> repo.saveClosing(date, entries, "NET_CALCULATED"));
    }
    var entries = repo.closingEntries(date);
    if (!"SETTLED".equals(repo.closingStatus(date).orElse(""))) {
      var bok =
          network.send(
              Envelope.of(
                  Header.request(MessageType.NET_SETTLEMENT, "NET-" + date, Institution.BOK),
                  new NetSettlement(date, entries)));
      if (bok.header().response() != ResponseCode.OK) return m.reply(bok.header().response());
      tx.executeWithoutResult(s -> repo.saveClosing(date, entries, "SETTLED"));
    }
    long total = entries.stream().mapToLong(e -> Math.max(0, e.netKrw())).sum();
    return m.reply(ResponseCode.OK, new DayCloseResult(date, "SETTLED", total, entries));
  }
}
