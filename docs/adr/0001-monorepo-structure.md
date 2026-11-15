# ADR 0001: 모노레포와 서비스별 소유권

- 날짜: 2026-11-15
- 상태: 채택

## 결정

1. delivery-agent 단일 모노레포. 서비스별 폴더와 CODEOWNERS로 소유권을 나눈다.
2. PGSystem은 레거시로 보관하고, PR #1의 결제 모듈만 이식한다.
3. API 계약은 각 서비스 폴더의 `api/`에 두고 그 서비스가 관리한다. 공용 contracts 모듈은 두지 않는다.
4. 안전장치는 CI의 스펙 diff 검사만 둔다. 호출자 리뷰는 강제하지 않는다.
5. 브랜치: feature → develop(squash) → main(merge commit, 시연 태그).
6. CI: GitHub Actions가 바뀐 서비스만 검사한다. Jenkins는 main 시연 배포만 맡는다.
7. ~~Playwright 워커는 독립 서비스(browser-worker)로, 로컬과 ECS에서 같은 계약으로 실행한다.~~ ADR 0002로 대체: 확장이 기본 실행기, browser-worker는 보조.
8. 앱·서버가 죽으면 워커도 세션 단위로 같이 종료된다(lease + heartbeat + 펜싱 토큰).
