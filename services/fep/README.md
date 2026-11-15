# fep

결제 쪽 묶음. payment-service → FEP → 기관(카드사·은행·결제원·한국은행)으로 전문을 중계한다.
PGSystem PR #1에서 아래 모듈을 이식했다.

| 모듈 | 역할 |
|---|---|
| `fep-contracts/` | 전문 헤더·응답 코드 (payment-service와 FEP가 같이 쓰는 유일한 결제 계약) |
| `fep-service/` | 전문 중계, 타임아웃, 장애 규칙 |
| `institution-simulator/` | 카드·은행·결제원·한국은행 모의 기관 |
