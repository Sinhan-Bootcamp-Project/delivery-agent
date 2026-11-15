package com.sinhan.institution.bank.application;

import com.sinhan.fep.contract.*;
import com.sinhan.fep.contract.Bodies.*;
import com.sinhan.institution.bank.domain.*;
import com.sinhan.institution.shared.*;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 가상 은행(VFI-02). 출금·입금은 결제원 전문(금융공동망)으로만 받는다. FEP 경유로는 잔액 조회와 일자별 내역만 허용한다.
 *
 * <p>모든 기장은 legId로 멱등이다. 같은 구간 전문이 다시 오면 다시 기장하지 않고 OK를 돌려준다.
 */
@Service
public class BankService implements MessageHandler {
  private final AccountRepository repo;
  private final TransactionTemplate tx;
  private final Faults faults;
  private final Clock clock;

  public BankService(
      AccountRepository repo, @Qualifier("bankTx") TransactionTemplate tx, Faults faults, Clock clock) {
    this.repo = repo;
    this.tx = tx;
    this.faults = faults;
    this.clock = clock;
  }

  @Override
  public Institution institution() {
    return Institution.BANK;
  }

  @Override
  public Envelope handle(Envelope m, Channel channel) {
    return switch (m.header().type()) {
      case WITHDRAW -> channel == Channel.INTERBANK ? post(m, -1) : m.reply(ResponseCode.NOT_ALLOWED);
      case DEPOSIT -> channel == Channel.INTERBANK ? post(m, 1) : m.reply(ResponseCode.NOT_ALLOWED);
      case TRANSACTION_INQUIRY ->
          repo.posting(m.header().transactionId())
              .map(v -> m.reply(ResponseCode.OK, new InquiryResult("POSTED", Math.abs(v), null)))
              .orElseGet(() -> m.reply(ResponseCode.NOT_FOUND));
      case BALANCE_INQUIRY -> {
        var no = m.read(BalanceInquiry.class).accountOrToken();
        yield repo.find(no)
            .map(a -> m.reply(ResponseCode.OK, new BalanceResult(no, a.balanceKrw())))
            .orElseGet(() -> m.reply(ResponseCode.NO_ACCOUNT));
      }
      case DAILY_TRANSACTIONS -> {
        var date = m.read(DailyQuery.class).businessDate();
        yield m.reply(ResponseCode.OK, new DailyTransactions(date, repo.daily(date)));
      }
      default -> m.reply(ResponseCode.NOT_ALLOWED);
    };
  }

  private Envelope post(Envelope m, int sign) {
    var p = m.read(AccountPosting.class);
    if (sign > 0 && faults.enabled() && faults.take("bank_deposit_fail"))
      return m.reply(ResponseCode.SYSTEM_ERROR);
    return tx.execute(
        s -> {
          var account = repo.lock(p.accountNo()).orElse(null);
          if (account == null) return m.reply(ResponseCode.NO_ACCOUNT);
          var existing = repo.posting(p.legId());
          if (existing.isPresent())
            return existing.get() == sign * p.amountKrw()
                ? m.reply(ResponseCode.OK)
                : m.reply(ResponseCode.DUPLICATE_CONFLICT);
          if (sign < 0) {
            var check = account.canWithdraw(p.amountKrw());
            if (check != ResponseCode.OK) return m.reply(check);
          } else if (p.amountKrw() <= 0) return m.reply(ResponseCode.INVALID_STATE);
          repo.post(
              p.legId(), p.accountNo(), sign * p.amountKrw(), p.memo(), BusinessDay.today(clock));
          return m.reply(ResponseCode.OK);
        });
  }
}
