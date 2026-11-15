package com.sinhan.institution.shared;

import com.sinhan.fep.contract.*;
import java.util.*;
import org.slf4j.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 헤더의 기관코드로 수신 기관을 찾는다. 같은 프로세스 안의 금융공동망 어댑터도 겸한다.
 *
 * <p>핸들러가 예외를 던지면 SYSTEM_ERROR로 응답한다. 호출자는 이것을 "모름"으로 처리해야 한다.
 */
@Component
public class InstitutionRouter implements InterbankNetwork {
  private static final Logger LOG = LoggerFactory.getLogger(InstitutionRouter.class);
  private final ObjectProvider<MessageHandler> provider;
  private volatile Map<Institution, MessageHandler> handlers;

  // 기관 서비스가 이 라우터(금융공동망)를 주입받으므로 핸들러 목록은 첫 전문 수신 때 찾는다.
  public InstitutionRouter(ObjectProvider<MessageHandler> provider) {
    this.provider = provider;
  }

  private Map<Institution, MessageHandler> handlers() {
    if (handlers == null) {
      var map = new EnumMap<Institution, MessageHandler>(Institution.class);
      provider.orderedStream().forEach(h -> map.put(h.institution(), h));
      handlers = map;
    }
    return handlers;
  }

  public Envelope receive(Envelope request, Channel channel) {
    Institution to;
    try {
      to = request.header().institution();
      request.header().type();
    } catch (IllegalArgumentException e) {
      return request.reply(ResponseCode.NOT_ALLOWED);
    }
    var handler = handlers().get(to);
    if (handler == null) return request.reply(ResponseCode.NOT_ALLOWED);
    try {
      return handler.handle(request, channel);
    } catch (RuntimeException e) {
      LOG.error(
          "institution={} type={} tx={} failed errorType={}",
          to,
          request.header().messageType(),
          request.header().transactionId(),
          e.getClass().getSimpleName());
      return request.reply(ResponseCode.SYSTEM_ERROR);
    }
  }

  @Override
  public Envelope send(Envelope request) {
    return receive(request, Channel.INTERBANK);
  }
}
