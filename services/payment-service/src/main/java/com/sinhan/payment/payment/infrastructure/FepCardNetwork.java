package com.sinhan.payment.payment.infrastructure;

import com.sinhan.fep.contract.*;
import com.sinhan.fep.contract.Bodies.*;
import com.sinhan.payment.payment.domain.CardNetwork;
import com.sinhan.payment.shared.FepGateway;
import org.springframework.stereotype.Component;

/** 카드사 전문 변환. 응답코드의 결과 분류(SUCCESS/DECLINED/NOT_FOUND/UNKNOWN)를 그대로 옮긴다. */
@Component
class FepCardNetwork implements CardNetwork {
  private final FepGateway fep;

  FepCardNetwork(FepGateway fep) {
    this.fep = fep;
  }

  @Override
  public Result approve(String id, String token, long amount, String merchantName) {
    var r = fep.send(MessageType.CARD_APPROVAL, id, Institution.CARD, new CardApproval(token, amount, merchantName));
    String approvalNo =
        r.header().response() == ResponseCode.OK ? r.read(CardApprovalResult.class).approvalNo() : null;
    return result(r, approvalNo, null);
  }

  @Override
  public Result cancel(String id, long amount) {
    return result(fep.send(MessageType.CARD_CANCEL, id, Institution.CARD, new CardCancel(id, amount)), null, null);
  }

  @Override
  public Result inquiry(String id) {
    var r = fep.send(MessageType.TRANSACTION_INQUIRY, id, Institution.CARD, java.util.Map.of());
    if (r.header().response() != ResponseCode.OK) return result(r, null, null);
    var body = r.read(InquiryResult.class);
    String approvalNo =
        body.detail() != null && body.detail().startsWith("approvalNo=") ? body.detail().substring(11) : null;
    return result(r, approvalNo, body.status());
  }

  @Override
  public long remainingLimit(String token) {
    var r = fep.send(MessageType.BALANCE_INQUIRY, "LIMIT-" + java.util.UUID.randomUUID(), Institution.CARD, new BalanceInquiry(token));
    return r.header().response() == ResponseCode.OK ? r.read(BalanceResult.class).balanceKrw() : -1;
  }

  private static Result result(Envelope r, String approvalNo, String status) {
    var code = r.header().response();
    var outcome =
        switch (code.outcome) {
          case SUCCESS -> Outcome.SUCCESS;
          case DECLINED -> Outcome.DECLINED;
          case NOT_FOUND -> Outcome.NOT_FOUND;
          case UNKNOWN -> Outcome.UNKNOWN;
        };
    return new Result(outcome, code.code, approvalNo, status);
  }
}
