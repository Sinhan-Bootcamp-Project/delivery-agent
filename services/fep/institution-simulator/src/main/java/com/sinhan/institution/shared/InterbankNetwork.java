package com.sinhan.institution.shared;

import com.sinhan.fep.contract.Envelope;

/** 기관 간 전문 송신 포트(금융공동망). 결제원→은행 출금/입금, 결제원→한국은행 차액, 카드사→결제원 대금 지급 이체. */
public interface InterbankNetwork {
  Envelope send(Envelope request);
}
