# 참고 자료와 확인할 코드

2026-10-23 조사 기준. GitHub는 동작 사례와 코드 근거이고, 공식 문서는 권한·한도·사용 계약을 확인하는 근거다. 공개 구현이 있다는 이유만으로 모든 환경의 로그인 성공이나 서비스 운영 허가가 증명되지는 않는다.

## 먼저 볼 저장소

| 자료 | 읽을 위치와 목적 |
| --- | --- |
| [som141/dis 인수인계 브랜치](https://github.com/som141/dis/tree/next-plan/maple-local-handoff) | `NEXT PLAN/`, `tools/maple-local-prototype/`, `docs/internal/analysis/`가 현재 작업의 기준 |
| [oyc0401/maple-auction-mcp](https://github.com/oyc0401/maple-auction-mcp/tree/d01119598479225946c3a7defa9be6f2f1b93295) | `server/src/auction/api.ts`, `mapping.ts`, `item.ts`, `service.ts`: 검색 입력/응답 매핑·장비 교체 연결. 현재 vendor는 이 MIT 커밋을 기준으로 함 |
| [lilllee/maple-auction](https://github.com/lilllee/maple-auction/tree/3ae0bf31b8ff762739e5af3db60484d861d9232f) | `src/nexon/browser.ts`, `src/watch/loop.ts`: 화면 있는 persistent browser, 수동 로그인, 브라우저 안 fetch, 감시 흐름 참고. 해당 코드 전체를 vendor한 것이 아님 |
| [SeleniumHQ/docker-selenium](https://github.com/SeleniumHQ/docker-selenium/blob/7d9aaf4cd13f119dce64eb258d665149131de011/README.md#using-your-browser-no-vnc-client-is-needed) | Linux 브라우저/noVNC 구성 참고. 우리 이미지는 이 Selenium 이미지를 사용하지 않고 자체 Node 이미지로 구성 |
| [microsoft/playwright](https://github.com/microsoft/playwright) · [공식 BrowserType API](https://playwright.dev/docs/api/class-browsertype#browser-type-launch-persistent-context) | persistent context·profile·실행 옵션·프록시·브라우저 호환성 확인 |

사용 중인 `playwright-core` 등 JS 버전은 [package-lock.json](../tools/maple-local-prototype/package-lock.json)을 따른다. 외부 코드를 추가로 가져오려면 해당 파일의 라이선스를 먼저 확인하고 출처·커밋·수정 내역을 기록한다. 참고 자료를 읽었다는 것과 우리 앱에서 실제 실행했다는 것을 구분한다.

## 공식 제공자 자료

- [Nexon Open API 메이플 문서](https://openapi.nexon.com/ko/game/maplestory/): 공개 캐릭터·장비 API와 최신 응답/한도 확인.
- [Nexon 이용 가이드](https://openapi.nexon.com/ko/guide/): Open ID, 애플리케이션·프렌즈·검수·scope 확인. 공개 API 키와 사용자 토큰은 다른 인증 수단이다.
- [공식 웹 경매장](https://auction.maplestory.nexon.com/): 로그인·OTP·세션·검색 잔여량을 실제 화면/응답으로 확인.
- [Nexon FAQ](https://openapi.nexon.com/ko/support/faq/): 서비스용 경매장 검색·주기 조회 권한의 공식 확인 경로.

조사한 공개 명세 56개 경로에서는 경매장 검색 API가 확인되지 않았다. 현재 프로토타입은 웹 내부 인터페이스를 사용한다. 공개 명세에 경매장이 추가됐는지는 새 작업 시 다시 확인한다. 웹 로그인 성공을 Open ID grant나 외부 개발자용 경매장 권한으로 해석하지 않는다.

## 기존 조사 문서

| 문서 | 필요한 시점 |
| --- | --- |
| [경매장 검색 API 상세](../docs/internal/analysis/MAPLE_AUCTION_SEARCH_API_REFERENCE_2026-10-23.md) | 필터 body·응답·페이지·검색 키·일일 한도 확인 |
| [Linux 운영 가능성](../docs/internal/analysis/MAPLE_LINUX_AUCTION_FEASIBILITY_2026-10-23.md) | 서버 브라우저·세션 지속·Windows 종료 이후의 구조 확인 |
| [넥슨 로그인 조사](../docs/internal/analysis/MAPLE_OPENID_LOGIN_CHECK_2026-10-23.md) | 계정 로그인·일반 API 키·사용자 토큰 범위 구분 |
| [경매장 AI 사례](../docs/internal/analysis/MAPLE_AUCTION_AI_CASES_2026-10-23.md) | 추천·장비 점수·공개 구현 비교 |
| [템환산 산식 공개 범위](../docs/internal/analysis/MAPLESCOUTER_GEAR_FORMULA_CHECK_2026-10-23.md) | 독립 모델과 환산주스탯 템환산의 차이 확인 |
| [기능 명세 V3](../docs/internal/analysis/MAPLE_GEAR_FEATURE_SPEC_2026-10-23.md) · [기술 명세 V3](../docs/internal/analysis/MAPLE_MARKET_TECH_SPEC_2026-10-23.md) | 로컬 실증 후 Discord 서비스 설계 검토 |

## 변경이 의심될 때의 조사 순서

1. 현재 브랜치 소스·vendor 수정 기록과 실패 단계를 확인한다.
2. 공식 화면/문서 및 인증된 응답에서 바뀐 부분을 확인한다. 비밀 값은 수집하지 않는다.
3. 관련 GitHub의 최신 커밋·이슈를 고정 커밋과 비교한다. 특히 `api.ts`의 클라이언트 버전, 검색 매핑, 브라우저/CORS 호환성을 본다.
4. 필요한 부분만 수정하고 근거·적용 커밋·검증 결과를 남긴다. 의존성을 일괄 최신화하거나 다른 프로젝트 전체를 덮어쓰지 않는다.

현재 관찰 기반 웹 클라이언트 버전은 1.0.2다. HTTP 426이 나면 변경을 조사하며, 검색 POST를 반복해 버전 문제를 해결하려 하지 않는다.
