# calculation-service

혜택 해석(③). 카드·쿠폰 혜택 조건을 해석해 최종 금액을 계산한다. 레거시 benefit-service를 재작성한다.

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
