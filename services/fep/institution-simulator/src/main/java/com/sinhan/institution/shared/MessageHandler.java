package com.sinhan.institution.shared;

import com.sinhan.fep.contract.*;

/** 기관 1곳의 전문 수신 창구. 기관별 application 계층이 구현한다. */
public interface MessageHandler {
  Institution institution();

  Envelope handle(Envelope request, Channel channel);
}
