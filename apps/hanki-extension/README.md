# hanki-extension

한끼 크롬 확장. hanki-agent SDK를 이 확장 안의 모듈로 둔다.

- **UI**: 사이드 패널에서 프롬프트 입력, 추천 확인, 승인 카드("요기요 결제는 진행하지 않습니다")
- **실행**: 사용자 Chrome의 요기요 탭을 `chrome.debugger`(CDP)로 조작한다
  - 읽기: `Accessibility.getFullAXTree` (a11y 트리 텍스트)
  - 쓰기: `Input.dispatchMouseEvent`, `Input.insertText`
  - 탭 위에 "디버깅 중" 배너가 뜨며, 시연에서는 "에이전트가 조작 중" 표시로 쓴다
- 사용자 본인 로그인 세션과 가정용 IP로 동작하므로 서버 headless에서 생기는 Akamai 403을 피한다
- 주문·결제 버튼은 클릭하지 않는다. 장바구니까지만 진행한다

## 설치 (스토어 등록 없음)

1. 빌드해서 `dist/`를 만든다 (빌드 도구는 TBD)
2. `chrome://extensions` → 개발자 모드 → "압축해제된 확장 프로그램 로드" → `dist/` 선택

`public/manifest.json`의 `key`를 고정해 두어서, 누가 어느 경로에서 로드해도 확장 ID가 같다.

| 항목 | 값 |
|---|---|
| 확장 ID | `kkhadhncbfbbpbpndgfeimgandbnblgf` |
| agent-service 허용 출처 | `chrome-extension://kkhadhncbfbbpbpndgfeimgandbnblgf` |

`key`는 공개키라 커밋해도 된다. 스토어에 올릴 일이 생기면 그때 새 키를 받는다.

## 계약

- agent-service가 보내는 액션: `services/agent-service/api/actions.schema.json`
- 이 확장과 `services/browser-worker`(Playwright, 보조)가 같은 액션 계약을 구현한다

## 생명주기

- agent-service와 WebSocket을 열고 5초마다 heartbeat를 보낸다. MV3 background는 30초쯤 유휴하면 잠들기 때문에, 열린 WebSocket의 주기적 메시지로 깨어 있게 한다
- 탭이 닫히거나 확장이 꺼지면 lease가 끊기고, agent-service는 15초 안에 세션을 끝낸다
- lease가 만료됐거나 `lease_epoch`가 다른 액션은 거부하고 `chrome.debugger.detach`로 조작을 멈춘다
