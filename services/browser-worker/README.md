# browser-worker

Playwright 워커. **보조 실행기**다. 기본 실행기는 `apps/hanki-extension`(chrome.debugger)이다.

용도:
- CI·E2E 테스트에서 확장 없이 액션 루프를 돌릴 때
- 시연 백업 (Mac headed Chromium에서 동작 확인됨. headless는 Akamai 403)

| 항목 | 값 |
|---|---|
| 언어 | Python |
| 담당 | CODEOWNERS 참고 |
| 계약 | `services/agent-service/api/actions.schema.json`을 구현한다 (확장과 같은 계약) |
| 포트 | TBD |

## 생명주기

부모(agent-service 또는 로컬 실행기) 프로세스의 자식으로 띄우고, stdin 파이프가 EOF가 되면 즉시 종료한다.
모든 액션의 `session_id`, `lease_epoch`를 검사하고 lease가 끊기면 브라우저를 닫는다.
