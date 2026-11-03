# 메이플 웹 경매장 검색 API 조사

2026-10-23 확인. **공식 웹 경매장이 사용하는 내부 HTTP API**에 대한 조사다. 외부 개발자에게 제공하는 Nexon Open API 계약과 구분한다. 실제 계정 로그인·인증된 검색·한도 차감·새 매물 반영 실험은 실행하지 않았다.

## 1. 근거와 확인 수준

| 근거 | 확인한 내용 | 한계 |
| --- | --- | --- |
| [공식 웹 경매장](https://auction.maplestory.nexon.com/)이 로드하는 [공개 JS 번들](https://auction.maplestory.nexon.com/assets/index-DPLLR4h6.js) | 검색 URL, 일일 한도 안내, 초기화 안내, 정렬 제한, 일부 오류 코드, 세션 방식 | 공개 화면·클라이언트 코드 확인. 서버의 실제 응답을 검증한 것은 아님 |
| [maple-auction-mcp](https://github.com/oyc0401/maple-auction-mcp/tree/d01119598479225946c3a7defa9be6f2f1b93295) | 요청 body 매핑, 응답 타입·축약 fixture, 검색 키 재사용, 인증 연결 | 독립 개발자의 2026-07 실측을 반영한 구현. 현재 서버 계약 보장 아님 |
| [Nexon Open API 메이플 문서](https://openapi.nexon.com/ko/game/maplestory/) | 별도 조사한 공개 명세 56개 경로 | 이 명세 범위에서는 경매장 검색·신규 매물 구독 API 미확인 |

공식 번들 SHA-256: `a4f70301ea56bef3292dd79a1e9f2760ce8d06625399f59eddd99a3090e72812`. 해시가 붙은 번들 URL은 사이트 배포 시 바뀔 수 있다.

## 2. 인증과 필요한 식별자

일반 `x-nxopen-api-key`를 넣는 공개 캐릭터 API와 다르다. 공개 구현은 사용자가 **공식 웹 경매장에 로그인한 Chrome의 세션**으로 요청한다. 확장은 `credentials: 'include'`로 브라우저가 세션 쿠키를 붙이게 한다. 쿠키를 서버로 추출해 보관하는 구조가 아니다.

공식 클라이언트에도 웹 세션 생성과 세션 만료 안내가 있고, 다른 환경에서 접속하면 기존 세션이 종료됐다는 안내가 있다. 단순 Nexon Open ID 로그인·UID 연결이 이 경매장 세션을 발급하거나 대체한다고 확인되지 않았다.

| 정보 | 용도 |
| --- | --- |
| 인증된 웹 경매장 세션 | 로그인 계정의 접근 권한 확인 |
| `worldId` | 검색 기준 월드. 월드 그룹에 따른 거래 범위도 적용 |
| `accountId` | 웹 경매장 계정 식별자 |
| `characterId` | 검색 기준 캐릭터 식별자 |
| `filters` | 이름·분류·가격·강화 조건 등 |
| `searchKey` | 검색 생성 응답에서 받은 후 페이지·정렬 조회에 사용 |

위 내부 `characterId`는 공개 캐릭터 API의 `ocid`와 같은 값으로 취급하지 않는다. 요청 식별자는 로그인 계정의 계정/월드별 캐릭터 조회에서 얻는 구현이다. 임의 닉네임의 OCID를 여기에 넣는 방식이 아니다.

따라서 `/아이템 캐릭터:닉네임`의 **분석 대상**과 경매장 요청의 **로그인 캐릭터**는 분리해야 한다. 둘이 다르면 경매장이 제공한 전투력 증가량을 입력 닉네임의 증가량으로 표시하지 않는다.

## 3. 확인한 엔드포인트

Base URL: `https://api.mskr.nexon.com/v1`

| 기능 | 메서드·경로 |
| --- | --- |
| 판매 중 매물 검색 생성 | `POST /market/web/items/searches/tool-tip` |
| 생성한 검색의 결과·정렬·페이지 | `GET /market/web/items/searches/{searchKey}/tool-tip` |
| 판매 완료 매물 검색 생성 | `POST /market/web/items/searches/sold/tool-tip` |
| 판매 완료 검색 결과·페이지 | `GET /market/web/items/searches/sold/{searchKey}/tool-tip` |
| 일일 잔여량 | `GET /market/web/daily-limit` |

GET 페이지 요청에는 공개 구현 기준 `page`, `limit`, `sortType`, `accountId`, `characterId`가 들어간다. 위 경로가 존재한다는 것만으로 외부 서버의 자동 조회 권한·안정성·호출량이 보장되지 않는다.

공식 화면은 검색어 또는 필터를 하나 이상 요구한다. 공개 구현은 검색어가 없으면 `keyword`를 생략하며, 빈 문자열은 검증 오류가 났다고 기록한다.

## 4. 검색 조건

아래 필드 매핑은 공개 구현의 [mapping.ts](https://github.com/oyc0401/maple-auction-mcp/blob/d01119598479225946c3a7defa9be6f2f1b93295/server/src/auction/mapping.ts) 기준이다. MCP 입력의 `priceMin` 같은 평면 이름과 실제 HTTP body의 `filters.price.min`을 혼동하지 않는다.

| 조건 | 실제 body의 필드 예 | 내용 |
| --- | --- | --- |
| 이름 | `filters.keyword`, `exactMatch` | 부분 검색·정확히 일치 |
| 부위·무기 종류 | `filters.itemCategory.itemDetailCategory` | 방어구·장신구·무기 세부 분류 |
| 직업군 | `filters.itemCategory.itemJobCategory` | 전사·마법사·궁수·도적·해적. 개별 직업명 필드와 구분 |
| 가격 | `filters.price.min`, `max` | 메소 단위 문자열 |
| 착용 레벨 | `filters.basicOption.levelMin`, `levelMax` | 아이템 요구 레벨 범위 |
| 스타포스 | `filters.enhancementOption.starforceMin`, `starforceMax` | 최소·최대 별 수 |
| 잠재·에디 등급 | `potentialGrade`, `additionalPotentialGrade` — `enhancementOption` 아래 | 공개 매핑: 0 없음 / 1 레어 / 2 에픽 / 3 유니크 / 4 레전드리 |
| 잠재·에디 옵션 | `potentialOptionSum`, `additionalPotentialOptionSum` 등 | 공/마%, 주스탯%, 보공, 방무, 크뎀 등. 합산·줄별 조건 매핑 |
| 추가 옵션·주문서 | `enhancementOption.exStr`, `scrollPhysicalAttack` 등 | 추옵·주문서 강화 수치, 잔여 강화 횟수 |
| 거래 조건 | `filters.etcOption` | 가위 횟수, 장착 시 교환 불가, 옵션 추출 가능 등 |
| 특수 반지 | `etcOption.seedRingLevelMin`, `seedRingLevelMax` | 반지 레벨 범위 |
| 내 월드만 | `filters.myWorldOnly` | 현재 기준 캐릭터 월드로 제한 |

캐시·코디에는 성별, 라벨 등급, 펫 등급, 기간제 옵션 필터도 구현돼 있다. 모든 옵션이 모든 분류에 적용되는 것은 아니다.

### 요청 예: 예산 30억 ±30%, 아케인 체인, 17성 이상

아래는 공개 매핑을 바탕으로 만든 **설명용 body**다. 계정·캐릭터 ID는 가상 값이며 실행/검증한 요청이 아니다.

```json
{
  "worldId": 0,
  "accountId": 12345,
  "characterId": 67890,
  "page": 1,
  "limit": 10,
  "sortType": "PRICE_PER_ITEM_ASC",
  "saveRecentKeyword": false,
  "filters": {
    "keyword": "아케인셰이드 체인",
    "exactMatch": true,
    "itemCategory": {
      "itemDetailCategory": "WEAPON_ONE_HANDED_CHAIN",
      "itemJobCategory": "THIEF"
    },
    "price": { "min": "2100000000", "max": "3900000000" },
    "enhancementOption": {
      "starforceMin": 17,
      "potentialGrade": 4,
      "potentialOptionSum": { "physicalAttackPercent": 21 }
    }
  }
}
```

`30억 ±30% = 21억~39억`이다. 이 조건으로 후보를 얻은 뒤 대상 캐릭터 장비와 교체 시 성능·가격을 우리 계산 모델로 비교한다. 여러 아이템 이름의 OR/일괄 검색은 이 구현에서 확인되지 않았다. UI의 다중 선택을 API 한 번 호출로 처리할 수 있다고 가정하지 않는다.

## 5. 반환 정보

공개 구현의 [item.ts](https://github.com/oyc0401/maple-auction-mcp/blob/d01119598479225946c3a7defa9be6f2f1b93295/server/src/auction/item.ts) 및 [실측 축약 fixture](https://github.com/oyc0401/maple-auction-mcp/blob/d01119598479225946c3a7defa9be6f2f1b93295/server/src/auction/summarize.test.ts) 기준이다. 아이템 종류에 따라 필드가 없거나 null일 수 있다.

| 분류 | 필드·내용 |
| --- | --- |
| 검색 전체 | `searchKey`, `total`, `page`, `totalPages`, `hasNext`, `items` |
| 매물 식별 | `_id`, `itemName` |
| 가격·수량 | `pricePerItem` — 개당 메소, 문자열 / `quantity` |
| 강화 | `starforce`, 주문서 성공·남은·실패·최대 횟수 |
| 스탯 | `toolTip.stat`의 STR/DEX/INT/LUK, HP, 공격력·마력, 보공·방무·데미지 등 |
| 잠재·에디 | `toolTip.upgradeInfo.potential`, `additionalPotential`의 등급·옵션 문구 |
| 추가 옵션 | `toolTip.upgradeInfo.exOption`의 옵션 내역 |
| 기타 강화 | 익셉셔널, 소울 정보 등 — 존재하는 아이템에 한함 |
| 거래 조건 | 거래 설명, 가위 잔여 횟수, `isMyWorld` 등 |
| 상태·시간 | `status` — 판매 중/판매 완료 등, `endDate`, 판매 완료 시 `tradeDate` 등 |
| 비교·관심 | `attackPowerDiff`, `wishlistCount` |

`endDate`는 판매 등록 만료 시각이고, `tradeDate`는 판매 완료 시각이다. 등록 시각·직접 매물 URL·전체 과거 체결 이력의 제공을 위 필드만으로 보장하지 않는다.

공식 화면은 전투력 증가량을 **기준 캐릭터의 마지막 로그아웃 시점**으로 안내한다. 이 값은 환산주스탯의 템환산·최종 데미지 개선율이 아니다. 공개 MCP의 `finalDamageChangeRate`는 별도 캐릭터 데이터와 자체 계산으로 붙인 값이며 경매장 원본 필드가 아니다.

## 6. 제한

| 항목 | 확인 내용·근거 |
| --- | --- |
| 일일 검색 | **하루 최대 100회** — 현재 공식 공개 화면의 안내 문구 |
| 초기화 | **매일 자정 00:00 자동 초기화** — 공식 안내. 정확한 서버 시간대는 별도 검증하지 않음 |
| 집계 대상 | 공개 개발자 설치 가이드는 ‘계정당’으로 설명. 넥슨 ID/메이플 계정/월드별 정확한 집계 경계는 인증 실험 필요 |
| 차감 지점 | 공개 구현은 검색 생성 POST가 1회를 차감하고 기존 키의 GET 페이지·정렬 조회는 차감하지 않는 것으로 설명 |
| 결과 0건 | 검색 생성 시 결과가 없어도 차감된다는 공개 구현의 안내 |
| 결과 페이지 | 공개 구현은 생성 시 10개, 후속 GET에서 20/40/60개를 사용. 현재 서버의 모든 허용값·총 조회 상한을 검증한 것은 아님 |
| 검색 키 | 만료 가능. 정확한 TTL 미확인. 재생성 POST가 필요하면 검색 횟수를 추가 사용 |
| 전투력 정렬 | 공식 안내는 결과 **500개 이하**일 때 제공한다고 설명. 실제 UI 분기는 `total < 500`이라 500개 경계 동작은 검증 필요 |
| 결과 과다 | 범위를 좁히라는 공식 안내가 있고, 공개 구현은 HTTP 422 / code 4040을 처리. 고정 최대 건수 미확인 |
| 순간 호출 제한 | 공식 공개 코드에 `MARKET_SEARCH_THROTTLED`(4041) 존재. 허용 RPS·동시 요청 수·최소 간격 미확인 |
| 한도 초과 | 공식 공개 코드에 `DAILY_SEARCH_LIMIT_EXCEEDED`(4042) 존재 |
| 시세 기간 | 공개 MCP는 최근 3개월로 설명하나 현재 공식 공개 안내에서 기간 상한을 확인하지 못함 |
| 세션 | 다른 환경 접속으로 종료될 수 있음. 정확한 수명·서버 보관/갱신 계약 미확인 |

공식 안내의 판매 등록 한도는 하루 20회다. 이는 검색 100회와 다른 기능이며, 현재 추천·검색 봇에 판매 기능은 포함하지 않는다. 인게임 옥션은 해당 안내에서 횟수 제한 없이 이용 가능하다고 설명하지만, 이를 웹 검색 API의 제한과 섞지 않는다.

정렬은 공개 구현 기준 개당 낮은 가격순, 높은 가격순, 이름순, 전투력 증가량순, 판매 종료순, 최신 등록순을 지원한다. 판매 중 호가와 실제 판매 완료가를 혼합해 시세로 표시하지 않는다.

## 7. 우리 알림 설계에 주는 영향

사용자 요청에 따라 기본 감시 주기를 **30분**으로 정했다. 매번 새 검색을 생성할 때의 정기 검색량은 다음과 같다. 최초 기준선·직접 검색·시세 조회·재시도는 별도다.

| 별도 검색 조건 수 | 하루 정기 검색 생성 | 검색 100회/일 기준 |
| --- | ---: | --- |
| 1개 | 48회 | 다른 사용량을 뺀 잔여량 확인 필요 |
| 2개 | 96회 | 최초 조회·직접 검색 등을 더하면 한도에 근접 |
| 3개 | 144회 | 매 회차 새 검색 생성 방식으로는 초과 |

아이템 다중 선택이 별도 검색으로 분리되면 해당 검색 수를 모두 합산한다. 따라서 30분 주기만으로 모든 조건 수의 감시가 가능한 것은 아니며, 실제 잔여량에 따라 간격을 늘리거나 한도 대기 상태로 전환한다.

공개 MCP 코드는 기존 `searchKey`가 조건을 저장하고 GET은 실시간 결과를 반환한다고 설명한다. 그러나 다음은 우리 실험이나 공식 외부 API 계약으로 검증되지 않았다.

1. 같은 키를 재사용한 결과에 새 등록 매물이 언제 반영되는가.
2. 키 만료 시간과 세션 종료 시 복구 동작은 무엇인가.
3. GET 주기 조회의 허용 범위·별도 제한·제공자 조건은 무엇인가.
4. 브라우저 없이 우리 서버에서 사용할 수 있는 공식 인증·위탁 방식이 있는가.

제품의 기본 확인 주기는 30분으로 정하되, 이 검증 전에는 실제 외부 검색·서버 단독 24시간 감시가 동작한다고 표시하지 않는다. 지원 시에는 잔여량 기반 스케줄링, 만료 시 재인증 상태, 매물 ID 중복 제거, 초기 기준선 알림 억제를 적용한다. 공식 신규 매물 webhook/스트림 API는 조사한 공개 문서에서 확인하지 못했다.

[현재 기능 명세](MAPLE_GEAR_FEATURE_SPEC_2026-10-23.md) · [기술 명세](MAPLE_MARKET_TECH_SPEC_2026-10-23.md) · [인증 경로 구분](MAPLE_MARKET_API_CHECK_2026-10-23.md)
