# payment-service

모의결제 원장·환불(⑤). PGSystem PR #1에서 이식했다. 레거시 platform-contracts 중 쓰던 모델은 `shared/contract`로 흡수했다. 설계는 [docs/payment-core.md](../../docs/payment-core.md) 참고.

| 항목 | 값 |
|---|---|
| 언어 | Java |
| 담당 | CODEOWNERS 참고 |
| 계약 | `api/openapi.yaml` |
| 포트 | TBD |
| 의존 서비스 | TBD |
| 환경변수 | TBD |

## 실행

TBD

## 규칙

- 다른 서비스의 `src/`를 import하지 않는다. 다른 서비스는 그 서비스의 `api/` 계약으로만 부른다.
- `api/` 변경은 이 서비스 담당자가 관리한다. 깨지는 변경은 CI가 막는다.
