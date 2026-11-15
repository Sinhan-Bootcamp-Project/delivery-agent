# delivery-agent

요기요 웹 메뉴를 사용자 취향과 프롬프트로 골라 장바구니까지 담고, 결제 직전에 멈춘 뒤 모의 결제를 진행하는 AI 에이전트.

## 구조

```text
apps/hanki-extension/      한끼 크롬 확장 (UI + SDK + 요기요 조작, chrome.debugger)
services/
  agent-service/           Search agent (Python, LangGraph)
  browser-worker/          Playwright 워커 (Python, 보조 실행기: E2E·시연 백업)
  ai-router/               AI 추론 라우터 (Python)
  calculation-service/     혜택 해석 (Java)
  control-service/         스냅샷 해시 · 승인 토큰 (Java)
  payment-service/         모의결제 원장 (Java)
  fep/                     FEP · 기관 시뮬레이터 (Java)
libs/                      거의 안 바뀌는 공통 코드
infra/local, infra/aws     로컬 compose, AWS IaC
docs/                      아키텍처, ADR
```

각 서비스의 `api/` 폴더가 그 서비스의 계약이다.

- 아키텍처: [docs/architecture.md](docs/architecture.md)
- 결정 기록: [docs/adr](docs/adr)
- 협업 규칙: [CONTRIBUTING.md](CONTRIBUTING.md)
