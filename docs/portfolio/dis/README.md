# 윤영주 · 이력서 개정안과 DIS 포트폴리오

작성일: **2026-10-27**. 기존 [GitHub 이력서](https://github.com/som141/som-resume/blob/30de95910e5148397ddff4378de6894081cc91d0/README.md), DIS의 코드·운영 기록, 작성자가 확인한 제작 계기와 사용 범위를 바탕으로 작성했다.

## 먼저 읽을 파일

| 목적 | 파일 |
| --- | --- |
| 이력서 PDF | [YOON_YOUNGJU_RESUME.pdf](YOON_YOUNGJU_RESUME.pdf) |
| DIS 상세 포트폴리오 PDF | [DIS_PORTFOLIO.pdf](DIS_PORTFOLIO.pdf) |
| 브라우저에서 포트폴리오 보기 | [index.html](index.html) |
| 브라우저에서 이력서 보기 | [resume.html](resume.html) |
| 복사·수정할 이력서 원고 | [RESUME.md](RESUME.md) |
| 복사·수정할 포트폴리오 원고 | [PORTFOLIO.md](PORTFOLIO.md) |
| 챌린지 3개만 짧게 보기 | [CHALLENGES.md](CHALLENGES.md) |

GitHub는 HTML을 웹페이지로 실행하지 않으므로, GitHub에서는 PDF나 Markdown을 읽는다. HTML은 내려받아 로컬 웹서버로 열 수 있다. 서비스 기능을 실행하거나 로그인할 필요는 없다.

## 검토용 자료

- [기존 이력서 검토](RESUME_REVIEW.md): 오래된 설명·잘못된 링크·표현 범위를 고친 이유.
- [공개 이력서·포트폴리오 3개 비교](REFERENCE_ANALYSIS.md): 이동욱·남정호·황준일의 실제 본문에서 참고한 구성과 한계.
- [2024년 음악 봇 관련 공개 기록](ORIGIN_CONTEXT.md): 개인적인 제작 계기와 당시 외부 라이브러리의 문제 보고를 구분.
- [주장별 근거](EVIDENCE.md): 코드·수정 커밋·Actions·작성자 확인을 구분한 대조표.
- [문서 검증 결과](VALIDATION.md): 사실 검토와 HTML·PDF 확인 범위.

## 글에 반영한 사실

2024년 기존 Discord 봇이 동작하지 않던 경험이 직접 만들게 된 계기였고, 현재 지인 약 30명과 사용한다는 내용을 작성자가 확인했다. 2024년을 저장소의 개발 시작일로 바꾸지 않았으며, 약 30명을 동시 접속자나 월간 이용 지표로 바꾸지 않았다.

이력서는 DIS를 대표 프로젝트로 두고 전자결재·날씨 앱을 간결하게 소개한다. 기존 ERP의 깨진 링크를 교정했고, 날씨 API 연결을 자체 예측 알고리즘 개발로 확대하지 않았다. 확인되지 않은 성적·경력·기여율·성능 수치는 추가하지 않았다.

제출 전에는 본인의 연락처, 최신 학적·학점, 보조 프로젝트에서 맡은 구체적인 역할을 확인해 필요한 항목만 보강한다. 이 초안에는 전화번호를 복제하지 않았다.

## 원고 수정과 HTML 생성

`RESUME.md`와 `PORTFOLIO.md`가 원고다. 수정한 뒤 저장소 루트에서 다음을 실행하면 두 HTML을 다시 만들 수 있다.

```bash
python -m pip install -r docs/portfolio/dis/requirements-render.txt
python docs/portfolio/dis/build_documents.py
python -m http.server 18896 --bind 127.0.0.1 --directory docs/portfolio/dis
```

브라우저에서 `http://127.0.0.1:18896/`를 연다. HTML은 외부 폰트·CDN·외부 스크립트를 요청하지 않는다. 한글을 지원하는 시스템 폰트가 필요하다.

브라우저의 인쇄 기능으로 A4 PDF를 저장할 수 있다. 배경 그래픽을 켜고 브라우저 기본 URL/날짜 머리말은 끈다. 인쇄 CSS는 상세 포트폴리오를 여섯 장의 이야기로 나눈다. 원고를 늘렸다면 페이지 끝과 표가 잘리지 않는지 다시 확인한다.
