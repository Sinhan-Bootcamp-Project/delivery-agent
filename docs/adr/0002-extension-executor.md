# ADR 0002: 크롬 확장을 기본 실행기로

- 날짜: 2026-11-15
- 상태: 채택 (ADR 0001의 7번을 대체)

## 결정

1. hanki-agent SDK는 크롬 확장(`apps/hanki-extension`, MV3)으로 넣는다. 스토어 등록 없이 개발자 모드 "압축해제된 확장 프로그램 로드"로 설치한다.
2. 확장이 UI와 요기요 조작을 함께 맡는다. 조작은 `chrome.debugger`(CDP)로, 검증된 "a11y 트리 + JSON 액션 루프"를 옮긴다.
3. Playwright `browser-worker`는 보조 실행기(E2E, 시연 백업)로 내린다.
4. 실행기가 둘이므로 액션 계약은 명령을 보내는 agent-service가 갖는다(`services/agent-service/api/actions.schema.json`).
5. manifest의 `key`를 고정해 확장 ID를 모두 같게 한다. agent-service는 그 출처만 허용한다.

## 이유

- PoC에서 headless Chromium은 Akamai 403. 사용자 실제 브라우저·본인 세션·가정용 IP가 가장 확실한 우회다.
- 원래 목표 흐름(메뉴 → 옵션 → 담기를 화면에서 보이게)과 맞는다.

## 대가

- "Playwright는 사용자에게 보이지 않게"라는 이전 결정은 뒤집힌다. 조작이 화면에 보이고 "디버깅 중" 배너가 뜬다.
- MV3 background가 유휴 시 잠들므로 heartbeat는 WebSocket으로 유지해야 한다.
