# 넥슨 로그인 연결·닉네임 검색 확인

2026-10-23 · 공식 공개 문서 데이터 조사. 실제 넥슨 로그인·인가 코드 교환·토큰 갱신은 실행하지 않았다.

## 결론

사용자가 API 키를 복사하지 않고 **넥슨 로그인과 정보 제공 동의**로 연결하는 공식 기능은 **NEXON Open ID**다. 사용자 개인의 Open API 키를 자동 발급·수집하는 기능이 아니라, 승인된 서비스가 사용자별 access/refresh token을 발급받는 OAuth 2.0 기반 경로다.

서비스는 Discord 로그인 ID와 넥슨 사용자 조회 응답의 `result.uid`를 연결해 DB에 보관할 수 있다. ID 연결은 유지할 수 있지만 접근 권한은 scope·동의·토큰 만료/철회에 종속된다. 넥슨 로그인만으로 경매장 검색 세션이나 경매장 서버 API 권한이 생긴다고 가정하지 않는다.

## 확인한 공식 가이드 데이터

공식 [한국어 이용 가이드](https://openapi.nexon.com/ko/guide/)가 공개 응답으로 내려주는 `__NEXT_DATA__`의 한국어 `docsPage.openIdIntroductionGuide`, `docsPage.openIdDevelopmentGuide`, `openIdDevelopmentGuide`를 2026-10-23 다시 확인했다. 아래는 그 데이터에 명시된 내용이다.

| 항목 | 확인 내용 |
| --- | --- |
| 기능 | 넥슨 로그인부터 사용자 정보 제공 동의까지 OAuth 2.0/OIDC 기반으로 제공 |
| 이용 자격 | 지정된 프렌즈가 프렌즈 애플리케이션을 생성해야 함 |
| 개발 단계 | 등록된 테스트 사용자만 로그인·동의 가능. 제작자 외 최대 5인 안내 |
| 일반 공개 | 검수를 거쳐 프로덕션 단계로 전환해야 모든 넥슨 이용자에게 제공 |
| 사용자 식별 | 사용자 정보 조회 응답 `result.uid`와 실제 사용 중인 `result.scope` |
| 토큰 발급 | authorization code로 access/refresh token 발급 |
| 토큰 수명 | 가이드 설명: access 30분, refresh 14일. 런타임에서는 응답의 만료 필드를 기준으로 관리 |
| 갱신 | refresh 요청 응답에도 신규 access_token·refresh_token과 각 만료 정보가 포함됨 |
| 철회/만료 | refresh 만료 시 인가 코드 재발급 필요. 무기한 무인증 접근으로 표현하지 않음 |

공식 가이드 데이터는 “NEXON Open ID는 지정된 프렌즈만 접근 가능한 프렌즈 메뉴를 통해 제공됩니다”, “검수를 거쳐 애플리케이션을 프로덕션 단계로 만들면, 모든 넥슨 게이머 대상으로 … 적용”이라고 안내한다.

전용 [소개](https://openapi.nexon.com/ko/open-id/introduction-guide/)·[개발 가이드](https://openapi.nexon.com/ko/open-id/development-guide/)·[검수 가이드](https://openapi.nexon.com/ko/open-id/inspection-guide/)·[프렌즈 신청](https://openapi.nexon.com/ko/support/friends-registration/) 경로는 이 환경의 프록시가 403으로 거절했다. 해당 페이지 본문이나 실제 애플리케이션 관리 화면을 읽었다고 주장하지 않는다. 위 내용의 근거는 접속에 성공한 공식 공개 가이드 페이지가 포함한 가이드 데이터다. 승인 기간·우리 서비스 승인 가능 여부·실제 계정 응답은 미확인이다.

공개 가이드의 scope/API 표에는 `maplestory.characterlist`, `starforce`, `potential`, `cube`, `achievement`, `scheduler`, `soulpotential`이 있다. 이 표에서 경매장 scope는 확인되지 않았다. 사용자가 보낸 스타포스 사이트 화면은 이와 유사한 UX의 예시이지만, 이미지 하나로 해당 사이트의 실제 인증 구현을 확정하지 않는다.

## `/아이템 캐릭터:닉네임`은 기본적으로 별도 로그인 없이 설계할 수 있다

공식 [캐릭터 API YAML](https://openapi.nexon.com/static/api/maplestory/14_ko_script20261022040014.yaml)을 확인했다.

```text
GET /maplestory/v1/id?character_name={입력한 닉네임}
  서비스가 발급받은 일반 API 키 → ocid

GET /maplestory/v1/character/basic?ocid=...
GET /maplestory/v1/character/stat?ocid=...
GET /maplestory/v1/character/item-equipment?ocid=...
```

위 공개 조회는 문서에 `x-nxopen-api-key`와 nickname/ocid가 정의돼 있다. 서비스 소유의 API 키로 공개 캐릭터 데이터를 읽고 사용자가 입력한 캐릭터를 기준으로 비교하는 구조를 기본으로 한다. 대상 API의 공개 범위·호출량·데이터 이용 조건은 준수한다.

계정에 속한 내 캐릭터 목록이나 본인 동의가 필요한 이력은 Open ID의 해당 scope를 별도 사용한다. 사용자 입력 닉네임·OCID와 넥슨 UID는 다른 식별자다. 계정 캐릭터 목록과 OCID가 일치할 때만 `내 캐릭터 확인됨`이라고 표시한다.

## V3 설계에 반영할 구분

- 기본 검색: `/아이템 캐릭터:예시히어로` → 서비스 API 키로 캐릭터 snapshot → 입력 캐릭터 기준 아이템 검색.
- 선택 계정 연결: `/api` → 웹 Discord 로그인 → NEXON Open ID 로그인·동의 → Discord ID와 UID·scope·암호화 토큰 연결.
- 경매장 검색/감시 인증: 위 두 경로와 별도의 capability. 이전 조사에서 확인한 외부 제공자 검증 과제를 유지한다.

[기능 명세](MAPLE_GEAR_FEATURE_SPEC_2026-10-23.md) · [기술 명세](MAPLE_MARKET_TECH_SPEC_2026-10-23.md)
