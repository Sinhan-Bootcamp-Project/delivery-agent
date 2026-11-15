package com.sinhan.payment.payment.domain;

import com.sinhan.payment.shared.contract.Models.ExecutionCommand;

/** 사람이 승인한 주문안인지 확인하는 포트(control-service 승인 토큰). 승인이 없거나 바뀌었으면 예외. */
public interface ApprovalCheck {
  void verify(ExecutionCommand command);
}
