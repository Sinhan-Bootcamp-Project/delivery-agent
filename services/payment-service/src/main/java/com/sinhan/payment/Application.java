package com.sinhan.payment;

import com.sinhan.web.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 결제 서비스(Main망 + FEP망). 바운디드 컨텍스트 5개를 한 프로세스에 둔다.
 *
 * <ul>
 *   <li>payment: 승인된 주문의 모의 결제·조회·취소
 *   <li>paymoney: 자체 선불 충전금 충전·잔액
 *   <li>ledger: 복식부기 원장(다른 컨텍스트는 application.LedgerService로만 기장)
 *   <li>settlement: 23:30 일 마감·대사·충전금 검사
 *   <li>outbox: 결제 결과 이벤트 전달
 * </ul>
 *
 * 경계 규칙(BoundaryTest): 다른 컨텍스트의 domain·infrastructure를 직접 쓰지 않고 application 계층만 호출한다.
 */
@SpringBootApplication
@EnableScheduling
@Import({SecurityConfiguration.class, ErrorHandler.class, TraceFilter.class, ServiceHttp.class})
public class Application {
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
