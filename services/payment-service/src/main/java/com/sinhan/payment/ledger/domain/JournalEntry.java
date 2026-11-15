package com.sinhan.payment.ledger.domain;

import java.util.*;

/**
 * 분개 1건(LED-01). 차변 합 = 대변 합이어야 만들어진다. 한 번 기록하면 수정·삭제하지 않고 역분개로만 정정한다(LED-02).
 *
 * @param referenceId 업무 거래ID(결제 executionId, 충전 ID 등). (referenceId, kind)는 유일해서 같은 분개가 두 번 생기지 않는다.
 * @param amountKrw 대표 금액(결제·충전 금액). 조회·대사 편의용이며 차대 합계와 별개.
 */
public record JournalEntry(String referenceId, Kind kind, long amountKrw, List<Line> lines) {
  public enum Kind {
    OPENING,
    PAYMENT,
    CANCEL,
    TOPUP,
    CARD_PAYOUT
  }

  public record Line(String account, AccountType type, long debitKrw, long creditKrw) {
    public Line {
      if (debitKrw < 0 || creditKrw < 0 || (debitKrw == 0) == (creditKrw == 0))
        throw new IllegalArgumentException("line must have exactly one positive side");
    }

    public static Line debit(String account, AccountType type, long amount) {
      return new Line(account, type, amount, 0);
    }

    public static Line credit(String account, AccountType type, long amount) {
      return new Line(account, type, 0, amount);
    }

    public long balanceDelta() {
      return type.delta(debitKrw, creditKrw);
    }
  }

  public JournalEntry {
    lines = List.copyOf(lines.stream().filter(Objects::nonNull).toList());
    if (lines.isEmpty()) throw new IllegalArgumentException("empty entry");
    long debit = lines.stream().mapToLong(Line::debitKrw).sum();
    long credit = lines.stream().mapToLong(Line::creditKrw).sum();
    if (debit != credit) throw new IllegalArgumentException("debit " + debit + " != credit " + credit);
  }

  /** 역분개: 차변과 대변을 바꾼 분개. */
  public JournalEntry reverse(Kind kind) {
    return new JournalEntry(
        referenceId,
        kind,
        amountKrw,
        lines.stream().map(l -> new Line(l.account(), l.type(), l.creditKrw(), l.debitKrw())).toList());
  }

  /** 0원 줄을 허용하지 않으므로 금액이 0이면 줄을 만들지 않는다. */
  public static Line debitIfPositive(String account, AccountType type, long amount) {
    return amount > 0 ? Line.debit(account, type, amount) : null;
  }

  public static Line creditIfPositive(String account, AccountType type, long amount) {
    return amount > 0 ? Line.credit(account, type, amount) : null;
  }
}
