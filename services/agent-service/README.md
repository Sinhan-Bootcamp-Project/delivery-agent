# agent-service

Search agent. LangGraph로 조건 추출(①), 검색/색출(②), 추천(③), 장바구니(④)를 조율한다.

| 항목 | 값 |
|---|---|
| 언어 | Python |
| 담당 | CODEOWNERS 참고 |
| 계약 | `api/openapi.yaml` (앱·확장용 API), `api/actions.schema.json` (실행기에 보내는 액션) |
| 포트 | TBD |
| 의존 서비스 | TBD |
| 환경변수 | TBD |

## 실행

TBD

## 규칙

- 다른 서비스의 `src/`를 import하지 않는다. 다른 서비스는 그 서비스의 `api/` 계약으로만 부른다.
- `api/` 변경은 이 서비스 담당자가 관리한다. 깨지는 변경은 CI가 막는다.
