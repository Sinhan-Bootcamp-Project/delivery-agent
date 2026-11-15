package com.sinhan.institution;

import com.sinhan.web.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 가상 금융기관 4곳을 한 프로세스로 띄운다(로컬 VM 메모리 예산 때문).
 *
 * <p>경계 규칙: card / bank / kftc / bok 패키지는 서로 import하지 않는다. 기관 간 통신은 {@code
 * shared.InterbankNetwork}(금융공동망 역할) 전문으로만 한다. 각 기관은 자기 DataSource(별도 DB 계정·스키마)만 쓴다.
 * 이 규칙은 {@code BoundaryTest}가 검사한다. 기관을 별도 jar로 나눌 때는 InterbankNetwork 어댑터만 HTTP로 바꾸면 된다.
 */
// 기관별 DataSource를 직접 만들므로 단일 DataSource 자동 구성은 끈다.
@SpringBootApplication(
    exclude = {
      org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
      org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration.class
    })
@Import({SecurityConfiguration.class, ErrorHandler.class, TraceFilter.class})
public class Application {
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
