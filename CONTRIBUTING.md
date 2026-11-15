# 협업 규칙

## 브랜치

- `main`: 시연용. `develop`에서만 들어온다.
- `develop`: 통합. PR + 리뷰 1명 + `ci-gate` 통과 필수.
- 작업 브랜치: `<type>/<서비스>-<이슈번호>-<짧은설명>` (예: `feat/agent-12-budget-filter`)
- feature → develop: squash merge. develop → main: merge commit, 시연 직전 태그 `demo-YYYYMMDD`.
- 한 PR은 한 서비스 폴더만 바꾼다. 계약 변경은 `api/`와 호출하는 쪽 수정을 같은 PR에 담아도 된다.
- 브랜치 수명은 2일 이내 권장. 길어지면 develop을 rebase로 받는다.

## 커밋

`<type>: <Header>` (type: feat, refactor, fix, style, chore, tests, docs)
헤더는 50자 이하, 대문자로 시작, 명령형, 마침표 없음. 본문은 한 줄 72자 이하.

## 경계

1. 서비스는 다른 서비스의 `src/`를 import하지 않는다. 계약(`api/`)으로만 부른다.
2. `libs/`에는 도메인 모델을 넣지 않는다.
3. DB는 서비스별 스키마와 계정. 다른 서비스 스키마 접근 금지.
4. 각 서비스 README에 실행 방법, 포트, 의존 서비스, 환경변수를 적는다.

## 계약

- `services/<name>/api/`는 그 서비스 담당자가 관리한다.
- PR에서 `api/`가 바뀌면 CI가 base 브랜치와 비교해 깨지는 변경(필드 삭제, 타입 변경, 필수 필드 추가)을 실패 처리한다.

## 이름 규칙

- DB 컬럼: snake_case 소문자, 생성 시각은 `created_at`, 날짜는 UTC ISO.
- Python 함수: 동사+명사 snake_case.
