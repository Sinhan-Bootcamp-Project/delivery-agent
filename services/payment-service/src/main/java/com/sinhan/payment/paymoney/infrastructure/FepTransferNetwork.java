package com.sinhan.payment.paymoney.infrastructure;

import com.sinhan.fep.contract.*;
import com.sinhan.fep.contract.Bodies.*;
import com.sinhan.payment.paymoney.domain.TransferNetwork;
import com.sinhan.payment.shared.FepGateway;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class FepTransferNetwork implements TransferNetwork {
  private final FepGateway fep;
  private final String trustAccount;

  FepTransferNetwork(FepGateway fep, @Value("${paymoney.trust-account:088-9000-000001}") String trustAccount) {
    this.fep = fep;
    this.trustAccount = trustAccount;
  }

  @Override
  public Result topUp(String id, String fromAccount, long amount) {
    var r =
        fep.send(
            MessageType.TRANSFER,
            id,
            Institution.KFTC,
            new Transfer(Institution.BANK.code, fromAccount, Institution.BANK.code, trustAccount, amount, "TOPUP"));
    var code = r.header().response();
    return switch (code.outcome) {
      case SUCCESS -> new Result(Outcome.COMPLETED, code.code);
      case DECLINED -> new Result(Outcome.FAILED, code.code);
      case NOT_FOUND -> new Result(Outcome.NOT_FOUND, code.code);
      case UNKNOWN -> new Result(Outcome.UNKNOWN, code.code);
    };
  }

  @Override
  public Result inquiry(String id) {
    var r = fep.send(MessageType.TRANSACTION_INQUIRY, id, Institution.KFTC, Map.of());
    var code = r.header().response();
    if (code.outcome == ResponseCode.Outcome.NOT_FOUND) return new Result(Outcome.NOT_FOUND, code.code);
    if (code != ResponseCode.OK) return new Result(Outcome.UNKNOWN, code.code);
    var status = r.read(InquiryResult.class).status();
    return switch (status) {
      case "COMPLETED" -> new Result(Outcome.COMPLETED, code.code);
      case "FAILED", "COMPENSATED" -> new Result(Outcome.FAILED, r.read(InquiryResult.class).detail());
      default -> new Result(Outcome.UNKNOWN, status);
    };
  }

  @Override
  public OptionalLong trustBalance() {
    var r =
        fep.send(
            MessageType.BALANCE_INQUIRY, "TRUST-" + UUID.randomUUID(), Institution.BANK, new BalanceInquiry(trustAccount));
    return r.header().response() == ResponseCode.OK
        ? OptionalLong.of(r.read(BalanceResult.class).balanceKrw())
        : OptionalLong.empty();
  }
}
