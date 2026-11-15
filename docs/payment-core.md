# 결제·금융 코어 (payment-core)

승인된 장바구니를 **모의 결제**하고 돈과 거래 내역을 정확하게 관리한다. 요기요 결제는 어떤 컴포넌트도 호출하지 않는다.
요구사항: Notion 기능 명세서 v0.1, `requirements/payment-core-requirements-v0.2` (F-01~F-13, N-01~N-03).
아키텍처: Figma "한끼 | 결제 프로세스", "한끼 | AI 에이전트 시스템 아키텍처".

## 1. 실행 형태

모든 Java 서비스는 **Spring Boot 실행형 jar(내장 Tomcat)** 다. `mvn package` → `java -jar <service>-0.1.0.jar`,
컨테이너는 `services/Dockerfile.java`가 같은 jar를 실행한다. 외부 Tomcat(WAR) 배포는 쓰지 않는다.

## 2. 모듈과 담당 경계

```text
Main망 ─ payment-service ──(fep망)── fep-service ──(mock망)── institution-simulator
           │                              │                       ├ card  (가상 카드사)
           │ payments 스키마               │ fep 스키마             ├ bank  (가상 은행)
           │                              │                       ├ kftc  (가상 금융결제원)
           │                              │                       └ bok   (가상 한국은행, 23:30 배치 전용)
           └ order-worker / benefit-service가 호출                 기관마다 inst_* 스키마 + 별도 DB 계정
```

| 모듈 | 역할 | 패키지 경계 |
|---|---|---|
| `fep-contracts` | 전문 공통 헤더·응답코드·바디 record, 영업일 규칙, 마스킹. **업무 규칙 없음** | 세 서비스가 공유하는 유일한 코드 |
| `payment-service` | 결제·페이머니·원장·일마감·Outbox | `payment` / `paymoney` / `ledger` / `settlement` / `outbox` (각 domain·application·infrastructure·presentation) |
| `fep-service` | 단일 전문 창구, 기관 라우팅, 타임아웃(3초), 전문 로그(마스킹), 장애 주입 | domain(Relay 규칙) / application / infrastructure / presentation |
| `institution-simulator` | 가상 금융기관 4곳 | `card` / `bank` / `kftc` / `bok` / `shared` |

경계 규칙은 ArchUnit 테스트(`BoundaryTest`)가 빌드 때 검사한다.

- payment-service: 다른 컨텍스트의 `infrastructure`·`presentation`을 쓰지 않는다. 원장 기장은 `ledger.application.LedgerService`로만.
- institution-simulator: 기관 패키지끼리 import 금지. 기관 간 통신은 `shared.InterbankNetwork`(금융공동망) 전문으로만.
- 모든 `domain` 패키지는 Spring·infrastructure에 의존하지 않는다.

기관을 팀원별로 나눠 맡게 되면 `institution-simulator`를 기관별 jar로 쪼갠다. `InterbankNetwork` 어댑터를 HTTP로 바꾸고 FEP 라우팅 주소(`ROUTE_CARD` 등)만 바꾸면 된다.

## 3. 흐름

```text
[카드 결제]      payment → FEP → 카드사
[페이머니 결제]  payment 내부 원장만 (외부 호출 없음)
[페이머니 충전]  payment → FEP → 결제원 → 은행(사용자 계좌 출금 → 충전금 계좌 입금)
[23:30 일 마감]  카드사 마감(카드대금 지급 이체) → 결제원 차액 산출 → 한국은행 차액결제
                → 한국은행 대조 → 카드 대사 → 충전 대사 → 카드대금 기장 → 충전금 검사 → 원장 검사
```

결제 1건: 승인 토큰 확인(control-service) → **PENDING 커밋** → FEP → 기관 → APPROVED + 분개 + Outbox 한 트랜잭션.
응답이 없으면(9100) **재전송하지 않고** 같은 거래ID로 상태 조회 → 그래도 모르면 UNKNOWN → 복구 작업(15초 주기, 30초 경과 건) 또는 23:30 대사가 확정.

충전 중 입금 구간이 실패하면 결제원이 출금 구간을 역이체로 보상한다(Saga). 결제원 이체는 구간별 상태(debit/credit/compensation)를 남긴다.

## 4. 원장

복식부기. `journal_entries`/`journal_lines`는 트리거로 UPDATE·DELETE가 막혀 있고 분개별 차변=대변을 커밋 시점에 검사한다.
`accounts.balance_krw`는 분개 합의 캐시이며 `PAYMONEY:*`는 음수가 불가하다.

| 거래 | 차변 | 대변 |
|---|---|---|
| 페이머니 결제 | PAYMONEY:{user} 결제액, BENEFIT_EXPENSE 즉시할인 | MERCHANT_PAYABLE |
| 카드 결제 | CARD_RECEIVABLE 결제액, BENEFIT_EXPENSE 즉시할인 | MERCHANT_PAYABLE |
| 취소 | 원 분개의 역분개 | |
| 충전 | TRUST_DEPOSIT | PAYMONEY:{user} |
| 카드대금 입금 | SETTLEMENT_DEPOSIT | CARD_RECEIVABLE |

충전금 별도관리 검사: `sum(PAYMONEY:*) = 은행 충전금 계좌(088-9000-000001) 잔액`.

## 5. API

| 서비스 | 메서드·경로 | 권한 |
|---|---|---|
| 결제 | `POST /internal/v1/payments` (ExecutionCommand → PaymentResult) | payment:charge |
| 결제 | `GET /internal/v1/payments/{executionId}` | payment:read |
| 결제 | `POST /internal/v1/payments/{executionId}/cancel` (`/refund` 별칭) | payment:refund |
| 결제 | `GET /internal/v1/wallets/{userId}` (CARD_* 남은 한도, LOCAL·PAYMONEY 잔액) | wallet:read |
| 결제 | `POST /internal/v1/settlements/{yyyy-MM-dd}/run`, `GET /internal/v1/settlements/{date}` | settlement:run |
| 페이머니(공개) | `GET /v1/paymoney/balance` | 사용자 토큰 |
| 페이머니(공개) | `POST /v1/paymoney/charges` `{"amountKrw":10000}` + `Idempotency-Key` | 사용자 토큰 |
| 페이머니(공개) | `GET /v1/paymoney/charges/{id}` | 사용자 토큰 |
| FEP | `POST /fep/v1/messages` (Envelope → Envelope, HTTP는 항상 200, 결과는 헤더 응답코드) | fep:send |
| FEP | `POST/DELETE /fep/v1/test/faults`, `GET /fep/v1/messages?transactionId=` | test:admin |
| 기관 | `POST /institutions/v1/messages` (FEP 전용) | institution:message |

PaymentResult.status는 기존 계약을 유지한다: `PAID | REFUNDED | PENDING | UNKNOWN | CANCEL_PENDING`.
카드 거절은 409 `INSUFFICIENT_BALANCE`(한도·잔액) 또는 `PAYMENT_DECLINED`.

응답코드: `0000` 정상, `1001` 잔액부족, `1002` 한도초과, `1003` 정지, `1004` 계좌없음, `1005` 상태오류, `1009` 같은 거래ID 다른 내용,
`4040` 없음, `4050` 허용 안 됨, `9100` 타임아웃(모름), `9999` 시스템오류(모름).

## 6. 장애 주입(시험 환경, `ENABLE_FAULTS=true`)

```bash
# 카드 승인 응답 유실 1회
curl -X POST fep-service:8080/fep/v1/test/faults -H "Authorization: Bearer $ADMIN" \
  -d '{"institution":"CRD","messageType":"CARD_APPROVAL","mode":"RESPONSE_LOST","count":1}'
```

mode: `DELAY`(delayMs) / `NO_RESPONSE` / `RESPONSE_LOST` / `REJECT`(rejectCode). messageType을 비우면 그 기관의 모든 전문.
기관 내부 구간 실패: `POST institution-simulator:8080/institutions/v1/test/faults/bank_deposit_fail` (보상 시나리오), `bok_unavailable`.
`ALLOW_EARLY_CLOSE=true`면 23:30 전에 당일 마감을 실행할 수 있다(시연용).

## 7. 시연 기준과 자동 테스트

| 시연 | 테스트 |
|---|---|
| 승인 → 결제 → 원장 차변=대변 | `test_full_flow_double_approval_and_replay`, `test_database_isolation_and_ledger_balance` |
| 승인 토큰 없이 결제 → 거부 | `test_full_flow_double_approval_and_replay` (403) |
| 결제 버튼 2번 → 1건 | 같은 테스트(동시 승인 4회) |
| 응답 유실 → 상태 조회로 확정 | `test_card_response_loss_is_confirmed_by_inquiry`, `test_topup_response_loss_is_resolved_by_inquiry_without_resend` |
| 무응답 → UNKNOWN → 대사로 확정 | `test_zz_no_response_becomes_unknown_and_day_close_settles_it` |
| 취소 → 잔액 복원 | `test_rejected_order_and_lost_refund_response` |
| 충전 중 입금 실패 → 보상 | `test_credit_leg_failure_is_compensated` |
| 23:30 배치 → 대사·충전금 검사 통과 | `test_zz_...` |

```bash
./scripts/test-integration.sh   # 전체 스택 + tests/test_scenarios.py + tests/test_payment_core.py
mvn test                        # 도메인·경계 단위 테스트
```

## 8. 남은 일 / 합의 필요

- 주문 서비스의 Outbox 수신 엔드포인트(`OUTBOX_TARGET_URL`). 지금은 이벤트를 쌓기만 하고 order-worker는 조회 API로 확인한다.
- LOCAL(지역화폐)을 페이머니로 처리한 것은 잠정 결정.
- 망취소 자동화(F-12): 한끼 FAILED인데 기관 승인인 건은 대사 리포트에 STATUS_MISMATCH로만 남는다.
- 고정길이 전문(F-12)은 FEP 변환 계층에 추가 예정. 지금은 JSON 전문.
- 가맹점(요기요) 정산 지급은 범위 밖: MERCHANT_PAYABLE에 쌓인다.
- 은행이 1곳이라 충전(당행 이체)은 차액 0이다. 결제원 차액은 카드대금 지급(카드사→은행)에서만 생긴다.
