package com.sinhan.institution.bok.application;

import com.sinhan.fep.contract.*;
import com.sinhan.fep.contract.Bodies.*;
import com.sinhan.institution.bok.domain.*;
import com.sinhan.institution.shared.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 가상 한국은행(VFI-04). 23:30 배치 전용: 결제원이 보낸 차액결제 전문만 처리한다. 건별 결제 경로에는 없다.
 *
 * <p>FEP 경유로는 대사용 조회(일자별 배치 내역, 당좌 잔액)만 허용한다.
 */
@Service
public class BokService implements MessageHandler {
  private final CurrentAccountRepository repo;
  private final TransactionTemplate tx;
  private final Faults faults;

  public BokService(
      CurrentAccountRepository repo, @Qualifier("bokTx") TransactionTemplate tx, Faults faults) {
    this.repo = repo;
    this.tx = tx;
    this.faults = faults;
  }

  @Override
  public Institution institution() {
    return Institution.BOK;
  }

  @Override
  public Envelope handle(Envelope m, Channel channel) {
    return switch (m.header().type()) {
      case NET_SETTLEMENT -> channel == Channel.INTERBANK ? settle(m) : m.reply(ResponseCode.NOT_ALLOWED);
      case DAILY_TRANSACTIONS -> {
        var date = m.read(DailyQuery.class).businessDate();
        var records =
            repo.batch(date).stream()
                .flatMap(b -> b.entries().stream())
                .map(e -> new DailyRecord("NET-" + date, e.institution(), e.netKrw(), "SETTLED"))
                .toList();
        yield m.reply(ResponseCode.OK, new DailyTransactions(date, records));
      }
      case BALANCE_INQUIRY -> {
        var inst = m.read(BalanceInquiry.class).accountOrToken();
        yield repo.balance(inst)
            .map(b -> m.reply(ResponseCode.OK, new BalanceResult(inst, b)))
            .orElseGet(() -> m.reply(ResponseCode.NO_ACCOUNT));
      }
      default -> m.reply(ResponseCode.NOT_ALLOWED);
    };
  }

  private Envelope settle(Envelope m) {
    if (faults.enabled() && faults.take("bok_unavailable")) return m.reply(ResponseCode.SYSTEM_ERROR);
    var body = m.read(NetSettlement.class);
    SettlementBatch batch;
    try {
      batch = new SettlementBatch(body.businessDate(), body.entries());
    } catch (IllegalArgumentException e) {
      return m.reply(ResponseCode.INVALID_STATE);
    }
    return tx.execute(
        s -> {
          var existing = repo.batch(batch.businessDate());
          if (existing.isPresent())
            return existing.get().sameAs(batch.entries())
                ? m.reply(ResponseCode.OK)
                : m.reply(ResponseCode.DUPLICATE_CONFLICT);
          for (var e : batch.entries())
            if (repo.balance(e.institution()).isEmpty()) {
              s.setRollbackOnly();
              return m.reply(ResponseCode.NO_ACCOUNT);
            }
          repo.apply(batch);
          return m.reply(ResponseCode.OK);
        });
  }
}
