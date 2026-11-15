# browser-worker

Playwright 워커. 요기요 웹에서 click/type/snapshot 액션을 수행한다. 로컬 PC와 ECS 양쪽에서 같은 계약으로 실행한다.

| 항목 | 값 |
|---|---|
| 언어 | Python |
| 담당 | CODEOWNERS 참고 |
| 계약 | `api/actions.schema.json` |
| 포트 | TBD |
| 의존 서비스 | TBD |
| 환경변수 | TBD |

## 실행

TBD

## 규칙

- 다른 서비스의 `src/`를 import하지 않는다. 다른 서비스는 그 서비스의 `api/` 계약으로만 부른다.
- `api/` 변경은 이 서비스 담당자가 관리한다. 깨지는 변경은 CI가 막는다.
