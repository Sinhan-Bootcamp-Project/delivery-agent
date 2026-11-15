# background (MV3 service worker)

확장의 실행부. `chrome.debugger`(CDP) 호출은 모두 여기서 한다.
사이드 패널은 닫히면 같이 사라지므로 조작을 맡기지 않는다.

| 폴더 | 역할 |
|---|---|
| `cdp/` | `chrome.debugger.attach/sendCommand/detach` 래퍼. a11y 트리 읽기(`Accessibility.getFullAXTree`), 클릭·입력(`Input.dispatchMouseEvent`, `Input.insertText`) |
| `agent-link/` | agent-service와의 WebSocket. 액션 수신, 결과 전송, 5초 heartbeat(서비스 워커가 잠들지 않게 하는 역할도 겸함) |
| `guard/` | 액션 실행 전 검사. lease 만료·`lease_epoch` 불일치 거부, 주문·결제 버튼 클릭 차단, 같은 액션 반복 차단 |

흐름: `agent-link`가 액션 수신 → `guard` 통과 → `cdp`로 실행 → 결과(새 a11y 스냅샷)를 `agent-link`로 회신.
무엇을 클릭할지는 agent-service가 정한다. 이 폴더는 판단하지 않고 실행과 안전 검사만 한다.
