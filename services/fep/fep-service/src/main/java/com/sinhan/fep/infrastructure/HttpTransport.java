package com.sinhan.fep.infrastructure;

import com.sinhan.fep.contract.*;
import com.sinhan.fep.domain.Relay;
import com.sinhan.web.ServiceHttp;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

/** 기관 라우팅(FEP-02). 기관코드 → Mock망 주소. 기관을 jar로 나누면 여기 주소만 바뀐다. */
@Component
class HttpTransport implements Relay.Transport {
  private static final Logger LOG = LoggerFactory.getLogger(HttpTransport.class);
  private final RestClient.Builder builder;
  private final ServiceHttp http;
  private final Map<Institution, String> routes = new EnumMap<>(Institution.class);

  HttpTransport(
      RestClient.Builder builder,
      ServiceHttp http,
      @Value("${fep.routes.card}") String card,
      @Value("${fep.routes.bank}") String bank,
      @Value("${fep.routes.kftc}") String kftc,
      @Value("${fep.routes.bok}") String bok) {
    this.builder = builder;
    this.http = http;
    routes.put(Institution.CARD, card);
    routes.put(Institution.BANK, bank);
    routes.put(Institution.KFTC, kftc);
    routes.put(Institution.BOK, bok);
  }

  @Override
  public Optional<Envelope> send(Envelope request, Duration timeout) {
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
    factory.setReadTimeout(timeout);
    try {
      return Optional.ofNullable(
          builder
              .clone()
              .requestFactory(factory)
              .build()
              .post()
              .uri(routes.get(request.header().institution()) + "/institutions/v1/messages")
              .headers(h -> h.setBearerAuth(http.serviceToken()))
              .contentType(MediaType.APPLICATION_JSON)
              .body(request)
              .retrieve()
              .body(Envelope.class));
    } catch (RestClientException e) {
      LOG.warn(
          "institution={} tx={} no response errorType={}",
          request.header().institutionCode(),
          request.header().transactionId(),
          e.getClass().getSimpleName());
      return Optional.empty();
    }
  }
}
