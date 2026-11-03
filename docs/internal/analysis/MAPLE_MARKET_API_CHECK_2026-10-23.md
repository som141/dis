# 경매장 API·개인 키 위탁·JDA 체크박스 확인

2026-10-23 확인. 사용자 요청은 **이미지 입력 없이 개인 키 연결 → 경매장 조건 검색 → 기간 감시·알림**이다. 이 문서는 연동의 존재와 인증 가능성을 구분한다. 실제 사용자 키·넥슨 로그인으로 인증 요청을 실행하지 않았다.

후속 V3에서는 개인 키 필수 입력을 [NEXON Open ID 로그인·토큰 연결](MAPLE_OPENID_LOGIN_CHECK_2026-10-23.md)로 수정했다. 기본 `/아이템 캐릭터` 공개 조회는 서비스 키를 쓴다. 아래 개인 키 위탁 조사는 BYOK를 채택하는 경우의 제약으로 보존하며, 경매장 인증이 별개라는 결론은 유지한다.

## 1. 경매장 검색 경로는 존재하지만 인증이 다르다

공개 프로젝트 [maple-auction-mcp README](https://github.com/oyc0401/maple-auction-mcp/blob/d01119598479225946c3a7defa9be6f2f1b93295/README.md)는 다음을 별도로 요구한다.

- 경매장 검색: 사용자의 Chrome에서 메이플 웹 경매장에 로그인하고 확장을 연결.
- 캐릭터·장비 조회: 넥슨 Open API 키 설정.

[확장 요청 코드](https://github.com/oyc0401/maple-auction-mcp/blob/d01119598479225946c3a7defa9be6f2f1b93295/extension/src/api.ts)는 브라우저의 `credentials: include`를 사용한다. [검색 구현](https://github.com/oyc0401/maple-auction-mcp/blob/d01119598479225946c3a7defa9be6f2f1b93295/server/src/auction/service.ts)은 검색 생성·결과 페이지·검색 잔여량·검색 키 재사용을 다룬다. 이것은 경매장 검색을 연결한 공개 구현의 근거이며, 넥슨이 외부 서버에 발급한 공식 API 계약이나 우리 서비스의 접근 허가를 증명하지 않는다.

웹 경매장 [공식 홈페이지](https://auction.maplestory.nexon.com/)는 SPA 셸이다. 후속 [검색 API 상세 조사](MAPLE_AUCTION_SEARCH_API_REFERENCE_2026-10-23.md)에서는 이 홈페이지가 제공하는 공개 JS 번들에서 **하루 검색 100회·매일 자정 초기화** 및 검색 URL을 확인했다. 로그인 사용자 검색 응답·실제 한도 차감·자동 주기 조회 가능 여부는 확인하지 않았다. 로그인 쿠키를 서버로 추출해 연동하지 않는다.

## 2. 넥슨 공개 API 명세 전체 확인

[한국어 메이플 API 페이지](https://openapi.nexon.com/ko/game/maplestory/)와 이 페이지가 공개한 YAML 8개 분류, 총 **56개 경로**를 확인했다. 이 범위에서 경매장 매물 검색·신규 매물 이벤트·경매장 카탈로그는 확인되지 않았다. 이는 비공개 제휴 API까지 없다는 뜻은 아니다.

| 분류 | 경로 수 | 공식 공개 명세 |
| --- | ---: | --- |
| 캐릭터 | 25 | [2026-10-22 명세](https://openapi.nexon.com/static/api/maplestory/14_ko_script20261022040014.yaml) |
| 유니온 | 4 | [명세](https://openapi.nexon.com/static/api/maplestory/15_ko_script20260618050040.yaml) |
| 길드 | 2 | [명세](https://openapi.nexon.com/static/api/maplestory/16_ko_script20260723040144.yaml) |
| 연무장 | 4 | [2026-10-22 명세](https://openapi.nexon.com/static/api/maplestory/59_ko_script20261022041825.yaml) |
| 확률 | 6 | [명세](https://openapi.nexon.com/static/api/maplestory/17_ko_script20261022040249.yaml) |
| 스케줄러 | 1 | [명세](https://openapi.nexon.com/static/api/maplestory/62_ko_script20260821005015.yaml) |
| 랭킹 | 6 | [명세](https://openapi.nexon.com/static/api/maplestory/18_ko_script20261022021131.yaml) |
| 공지 | 8 | [명세](https://openapi.nexon.com/static/api/maplestory/27_ko_script20251218035959.yaml) |

캐릭터 키를 등록했다고 경매장 검색·서버 상시 감시가 자동으로 활성화되는 설계를 만들지 않는다. `CHARACTER_DATA`와 `AUCTION_SEARCH`/`AUCTION_WATCH`는 별도 capability로 확인한다.

## 3. 개인 키를 우리 DB에 맡기는 조건

[한국어 이용약관 제5조 ①·②](https://openapi.nexon.com/ko/support/terms/)에는 기본적으로 키 제공·공유 및 타인이 발급한 키 사용을 제한하면서 다음 예외를 둔다.

> 일부 API의 경우 API Key 소유자가 공개를 허용할 경우 API Key를 타인에게 제공할 수 있으며, 타인에게 제공 가능한 API Key는 Open API 공지사항 또는 내 애플리케이션 페이지를 통해 확인할 수 있습니다.

따라서 **사용자가 동의했고 암호화했다는 사실만으로 모든 개인 키의 서버 위탁 사용이 허용되는 것은 아니다.** 해당 키·API의 공유 허용 여부 또는 제공자와의 계약을 확인해야 한다. 로그인 전 공개 자료만으로 개별 키의 예외 해당 여부는 확인할 수 없었다.

제품 요구인 `Discord 사용자 ID ↔ 암호화한 API 키` 구조는 명세에 유지한다. 실제 등록·외부 호출 활성화는 provider별 키 위탁 정책을 확인한 경우로 제한한다. 운영자 키로 일방적으로 대체하지 않는다.

제5조 ⑧의 접근·과부하 제한도 있어 주기 조회 허용 범위·호출량을 계약/문서에서 확인해야 한다. 현재 공개 구현에 검색 잔여량 개념이 있다는 이유로 임의의 무료 무제한 조회를 가정하지 않는다.

## 4. 체크박스는 JDA 업그레이드가 필요하다

현재 저장소는 JDA **6.3.1**이다. 이 버전에는 Container·StringSelectMenu·Modal·AttachmentUpload가 있으나 Checkbox/CheckboxGroup/RadioGroup의 타입·빌더는 없다.

공식 최신 릴리스 [v6.7.0](https://github.com/discord-jda/JDA/releases/tag/v6.7.0) 및 동일 태그 소스에서 아래를 확인했다.

- [Checkbox](https://github.com/discord-jda/JDA/blob/v6.7.0/src/main/java/net/dv8tion/jda/api/components/checkbox/Checkbox.java): Label 안에서 사용하는 단일 체크박스.
- [CheckboxGroup](https://github.com/discord-jda/JDA/blob/v6.7.0/src/main/java/net/dv8tion/jda/api/components/checkboxgroup/CheckboxGroup.java): Modal 안, 한 그룹 최대 10개 선택지.
- [RadioGroup](https://github.com/discord-jda/JDA/blob/v6.7.0/src/main/java/net/dv8tion/jda/api/components/radiogroup/RadioGroup.java): 기간처럼 하나만 선택할 때 사용.

V2 계획은 **JDA 6.7.0 업그레이드 및 기존 음악·주식·음성 회귀 검증**을 포함한다. 이번 문서 작업에서 실제 Gradle 버전이나 봇을 변경하지 않았다. 아이템 대량 선택은 체크박스 10개 제한과 다른 String Select의 25개 제한을 고려해 페이지와 전체 선택을 서버 상태로 관리한다.

[V2 기능 명세](MAPLE_GEAR_FEATURE_SPEC_2026-10-23.md) · [기술 명세·아키텍처](MAPLE_MARKET_TECH_SPEC_2026-10-23.md)
