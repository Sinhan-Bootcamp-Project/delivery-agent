package com.sinhan.payment.payment.infrastructure;

import com.sinhan.payment.shared.contract.Models.*;
import com.sinhan.payment.payment.domain.ApprovalCheck;
import com.sinhan.web.ServiceHttp;
import org.springframework.stereotype.Component;

/** control-service의 승인 확인. 승인 없음·만료·스냅샷 불일치면 control-service가 403/409를 돌려준다. */
@Component
class ControlApprovalCheck implements ApprovalCheck {
  private final ServiceHttp http;
  private final String url = ServiceHttp.env("CONTROL_URL", "http://control-service:8080");

  ControlApprovalCheck(ServiceHttp http) {
    this.http = http;
  }

  @Override
  public void verify(ExecutionCommand c) {
    http.post(url + "/internal/v1/authorizations/check", c, Grant.class);
  }
}
