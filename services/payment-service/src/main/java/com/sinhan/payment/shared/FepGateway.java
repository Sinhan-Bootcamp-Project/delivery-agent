package com.sinhan.payment.shared;

import com.sinhan.fep.contract.*;
import com.sinhan.web.*;
import org.slf4j.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * FEP 호출 어댑터. 기관은 직접 부를 수 없고(다른 네트워크) 이 클래스만 FEP를 부른다.
 *
 * <p>FEP에 닿지 못했거나 5xx면 응답코드 TIMEOUT(모름)으로 바꿔 돌려준다. 재전송하지 않는다.
 */
@Component
public class FepGateway {
  private static final Logger LOG = LoggerFactory.getLogger(FepGateway.class);
  private final ServiceHttp http;
  private final String url = ServiceHttp.env("FEP_URL", "http://fep-service:8080");

  public FepGateway(ServiceHttp http) {
    this.http = http;
  }

  public Envelope send(MessageType type, String transactionId, Institution to, Object body) {
    var request = Envelope.of(Header.request(type, transactionId, to), body);
    try {
      var response = http.post(url + "/fep/v1/messages", request, Envelope.class);
      if (response == null || response.header() == null) return request.reply(ResponseCode.TIMEOUT);
      return response;
    } catch (RestClientException | Problem e) {
      if (e instanceof Problem p && p.status < 500) throw p;
      LOG.warn("fep unreachable type={} tx={} errorType={}", type, transactionId, e.getClass().getSimpleName());
      return request.reply(ResponseCode.TIMEOUT);
    }
  }
}
