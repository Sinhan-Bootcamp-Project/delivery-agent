# hanki-app

한끼 앱과 hanki-agent SDK(클라이언트 모듈). 프롬프트 입력, 추천 확인, 승인 카드를 보여준다.
SDK는 별도 패키지로 두지 않고 이 폴더 안의 모듈로 둔다.

앱 형태(웹/데스크톱)와 언어는 미정이다.

## 생명주기

앱이 종료되면 agent-service로 가는 heartbeat가 끊기고, 세션의 browser-worker가 15초 안에 브라우저를 닫는다.
