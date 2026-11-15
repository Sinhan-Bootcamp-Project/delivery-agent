package com.sinhan.institution.card.application;

import com.sinhan.fep.contract.*;
import com.sinhan.fep.contract.Bodies.*;
import com.sinhan.institution.card.domain.*;
import com.sinhan.institution.shared.*;
import java.time.*;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 가상 카드사(VFI-01). 승인·취소·한도 조회·일자별 내역, 일 마감 시 카드대금을 한끼 정산계좌로 지급 이체한다.
 *
 * <p>지급 이체는 결제원(금융공동망)을 거친다. 카드사 자신의 지급 계좌는 {@value #SETTLEMENT_ACCOUNT}.
 */
@Service
public class CardService implements MessageHandler {
  public static final String SETTLEMENT_ACCOUNT = "CRD-SETTLE";
  public static final String HANKI_SETTLEMENT_ACCOUNT = "088-9000-000002";
  private final CardRepository repo;
  private final TransactionTemplate tx;
  private final InterbankNetwork network;
  private final Clock clock;

  public CardService(
      CardRepository repo,
      @Qualifier("cardTx") TransactionTemplate tx,
      InterbankNetwork network,
      Clock clock) {
    this.repo = repo;
    this.tx = tx;
    this.network = network;
    this.clock = clock;
  }

  @Override
  public Institution institution() {
    return Institution.CARD;
  }

  @Override
  public Envelope handle(Envelope m, Channel channel) {
    return switch (m.header().type()) {
      case CARD_APPROVAL -> channel == Channel.FEP ? approve(m) : m.reply(ResponseCode.NOT_ALLOWED);
      case CARD_CANCEL -> channel == Channel.FEP ? cancel(m) : m.reply(ResponseCode.NOT_ALLOWED);
      case TRANSACTION_INQUIRY -> inquiry(m);
      case BALANCE_INQUIRY -> limit(m);
      case DAILY_TRANSACTIONS ->
          m.reply(
              ResponseCode.OK,
              new DailyTransactions(
                  m.read(DailyQuery.class).businessDate(),
                  repo.daily(m.read(DailyQuery.class).businessDate())));
      case DAY_CLOSE -> channel == Channel.FEP ? close(m) : m.reply(ResponseCode.NOT_ALLOWED);
      case WITHDRAW -> settlementLeg(m, channel, -1);
      case DEPOSIT -> settlementLeg(m, channel, 1);
      default -> m.reply(ResponseCode.NOT_ALLOWED);
    };
  }

  private Envelope approve(Envelope m) {
    var body = m.read(CardApproval.class);
    String id = m.header().transactionId();
    return tx.execute(
        s -> {
          var card = repo.lockCard(body.cardToken()).orElse(null);
          if (card == null) return m.reply(ResponseCode.NO_ACCOUNT);
          var existing = repo.authorization(id).orElse(null);
          if (existing != null)
            return existing.sameRequest(body.cardToken(), body.amountKrw())
                ? m.reply(
                    ResponseCode.OK, new CardApprovalResult(existing.approvalNo(), card.maskedNo()))
                : m.reply(ResponseCode.DUPLICATE_CONFLICT);
          var decision = card.authorize(body.amountKrw());
          if (decision != ResponseCode.OK) return m.reply(decision);
          String approvalNo = "%08d".formatted(ThreadLocalRandom.current().nextInt(100_000_000));
          repo.approve(
              new Authorization(
                  id,
                  body.cardToken(),
                  body.amountKrw(),
                  approvalNo,
                  "APPROVED",
                  BusinessDay.of(clock.instant()),
                  null));
          repo.changeUsed(body.cardToken(), body.amountKrw());
          return m.reply(ResponseCode.OK, new CardApprovalResult(approvalNo, card.maskedNo()));
        });
  }

  private Envelope cancel(Envelope m) {
    var body = m.read(CardCancel.class);
    return tx.execute(
        s -> {
          var a = repo.authorization(body.originalTransactionId()).orElse(null);
          if (a == null) return m.reply(ResponseCode.NOT_FOUND);
          repo.lockCard(a.cardToken());
          a = repo.authorization(body.originalTransactionId()).orElseThrow();
          if (a.amountKrw() != body.amountKrw()) return m.reply(ResponseCode.DUPLICATE_CONFLICT);
          if (a.status().equals("CANCELLED")) return m.reply(ResponseCode.OK);
          repo.cancel(a.transactionId(), BusinessDay.of(clock.instant()));
          repo.changeUsed(a.cardToken(), -a.amountKrw());
          return m.reply(ResponseCode.OK);
        });
  }

  private Envelope inquiry(Envelope m) {
    return repo.authorization(m.header().transactionId())
        .map(
            a ->
                m.reply(
                    ResponseCode.OK,
                    new InquiryResult(a.status(), a.amountKrw(), "approvalNo=" + a.approvalNo())))
        .orElseGet(() -> m.reply(ResponseCode.NOT_FOUND));
  }

  private Envelope limit(Envelope m) {
    var token = m.read(BalanceInquiry.class).accountOrToken();
    return tx.execute(
        s ->
            repo.lockCard(token)
                .map(c -> m.reply(ResponseCode.OK, new BalanceResult(token, c.remainingKrw())))
                .orElseGet(() -> m.reply(ResponseCode.NO_ACCOUNT)));
  }

  /** 일 마감: 당일 승인 - 당일 취소 금액을 결제원 이체로 한끼 정산계좌에 지급. 영업일당 1회(멱등). */
  private Envelope close(Envelope m) {
    LocalDate date = m.read(DayClose.class).businessDate();
    if (!date.isBefore(BusinessDay.of(clock.instant())) && !DemoMode.earlyCloseAllowed())
      return m.reply(ResponseCode.INVALID_STATE);
    long amount = repo.payout(date).orElseGet(() -> repo.payoutAmount(date));
    if (amount <= 0) {
      tx.executeWithoutResult(s -> repo.recordPayout(date, amount, "SKIPPED"));
      return m.reply(ResponseCode.OK, new DayCloseResult(date, "SKIPPED", amount, java.util.List.of()));
    }
    var transfer =
        network.send(
            Envelope.of(
                Header.request(MessageType.TRANSFER, "CARDPAYOUT-" + date, Institution.KFTC),
                new Transfer(
                    Institution.CARD.code,
                    SETTLEMENT_ACCOUNT,
                    Institution.BANK.code,
                    HANKI_SETTLEMENT_ACCOUNT,
                    amount,
                    "CARD_PAYOUT")));
    String status = transfer.header().response() == ResponseCode.OK ? "PAID" : "PENDING";
    tx.executeWithoutResult(s -> repo.recordPayout(date, amount, status));
    if (!status.equals("PAID")) return m.reply(transfer.header().response());
    return m.reply(ResponseCode.OK, new DayCloseResult(date, status, amount, java.util.List.of()));
  }

  /** 결제원이 카드사 지급계좌에서 출금(또는 보상 입금)하는 구간. 카드사 자금은 한국은행 당좌로 정산되므로 잔액 검사 없이 기록만 남긴다. */
  private Envelope settlementLeg(Envelope m, Channel channel, int sign) {
    if (channel != Channel.INTERBANK) return m.reply(ResponseCode.NOT_ALLOWED);
    var p = m.read(AccountPosting.class);
    if (!SETTLEMENT_ACCOUNT.equals(p.accountNo())) return m.reply(ResponseCode.NO_ACCOUNT);
    tx.executeWithoutResult(s -> repo.recordSettlementLeg(p.legId(), sign * p.amountKrw()));
    return m.reply(ResponseCode.OK);
  }
}
