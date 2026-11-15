# 아키텍처

- Figma: 「한끼 | AI 에이전트 시스템 아키텍처」 https://www.figma.com/design/43KHQ2Z3xrtZLATKnL8LUo

## 단계

① 자연어 추출 → ② 검색/색출 → ③ 추천 → ④ 장바구니 → ⑤ 결제(모의)

## 서비스와 호출 관계

| 호출하는 쪽 | 제공하는 쪽 | 계약 파일 | 단계 |
|---|---|---|---|
| hanki-app(SDK) | agent-service | services/agent-service/api/openapi.yaml | ①③④ |
| hanki-app(SDK) | control-service | services/control-service/api/openapi.yaml | ④⑤ 승인 |
| agent-service | browser-worker | services/browser-worker/api/actions.schema.json | ②④ |
| agent-service | ai-router | services/ai-router/api/openapi.yaml | ①③ |
| calculation-service | ai-router | services/ai-router/api/openapi.yaml | ③ |
| agent-service | calculation-service | services/calculation-service/api/openapi.yaml | ③ |
| control-service | payment-service | services/payment-service/api/openapi.yaml | ⑤ |
| payment-service | fep-service | services/fep/fep-contracts | ⑤ |

## 원칙

예산·최소주문·금액은 코드가 계산하고, 최종 승인은 사람이 승인 토큰으로 한다. AI는 조건 추출·순위·혜택 해석만 맡는다.

## 생명주기

앱 → agent-service → browser-worker로 세션 lease와 heartbeat(5초 간격, 15초 만료)를 이어 붙인다.
워커의 모든 액션에는 `session_id`와 `lease_epoch`가 붙고, 만료되거나 epoch가 다르면 워커는 액션을 거부하고 브라우저를 닫는다.
워커 하나가 죽으면 그 세션만 실패로 끝낸다.
