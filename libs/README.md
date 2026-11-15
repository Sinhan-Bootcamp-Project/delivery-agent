# libs

거의 바뀌지 않는 공통 코드만 둔다. 도메인 모델과 API 계약은 넣지 않는다.

| 폴더 | 내용 |
|---|---|
| `java-web/` | 공통 에러 응답(Problem) 등. 레거시 platform-web 축소판 |
| `py-common/` | 인증 헬퍼 등. 레거시 python-common |

`java-web`에는 레거시 platform-contracts에서 `Canonical`(정렬된 JSON·해시)과 `ApiError`(에러 응답)만 옮겼다. 둘 다 도메인이 아닌 공통 형식이다.
