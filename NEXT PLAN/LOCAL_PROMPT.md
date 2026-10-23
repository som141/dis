# 로컬 Codex에 전달할 프롬프트

아래 내용을 그대로 전달한다. 먼저 이 저장소를 로컬 프로젝트로 열어야 한다.

```text
GitHub 저장소 https://github.com/som141/dis 의
next-plan/maple-local-handoff 브랜치에서 메이플 개인용 프로토타입 작업을 이어서 진행해줘.
현재 PC는 Windows이고 기본 메이플 캐릭터는 에반이였솜이야.

저장소가 없으면 위 브랜치로 clone하고, 이미 있으면 미커밋 작업을 먼저 확인해.
기존 변경을 강제로 지우지 말고 필요하면 별도 clone/worktree를 사용해.

다음 문서를 순서대로 읽어:
1. NEXT PLAN/README.md
2. NEXT PLAN/CURRENT_STATE.md
3. NEXT PLAN/LOCAL_WORKFLOW.md
4. NEXT PLAN/REFERENCES.md
5. NEXT PLAN/NEXT_PLAN.md
6. tools/maple-local-prototype/DOCKER.md 와 VALIDATION.md

우선 목표는 Windows Docker Desktop에서 프로토타입을 실제 실행하고,
내가 원격 Chromium의 공식 페이지에서 로그인·OTP를 완료하면
본인 캐릭터 조회 → 에반이였솜 선택 → 장비 조회 → 실제 매물 검색 →
교체 점수/가성비 대조 → 30분 감시 확인까지 진행하는 거야.

Docker Desktop/Linux 컨테이너 모드와 필요한 도구·포트를 점검하고 설치·빌드·실행해.
Windows에서 실행하므로 SSH 터널이나 compose.cloud.yaml은 필요 없어.
호스트 Node가 없으면 LOCAL_WORKFLOW.md의 Docker 비밀번호 생성 대체 명령을 참고해.
원격 화면은 http://127.0.0.1:17900/vnc.html?autoconnect=1&resize=scale 이야.

현재 구현과 GitHub 참고 자료도 확인해.
REFERENCES.md의 maple-auction-mcp, lilllee/maple-auction, Docker/Playwright 공식 자료와
Nexon 공식 문서를 참고하되, 필요할 때 최신 변경/이슈를 고정 커밋과 비교하고
근거가 있는 수정만 적용해. 추가로 가져오는 코드의 라이선스와 출처도 남겨.

클라우드에서는 컨테이너 실행·18개 테스트·가상 브라우저 흐름은 통과했지만,
signin.nexon.com 접속이 클라우드 프록시 CONNECT 403으로 막혀 실제 로그인은 미검증이야.
로컬에서는 네트워크와 실제 인증을 다시 확인해야 해.

로그인·OTP·API 키·원격 화면 비밀번호는 내가 해당 화면에서 직접 입력/확인하게 안내해.
비밀 값을 채팅·로그·Git에 남기거나 쿠키를 추출하지 마.
실제 계정 검색은 잔여량을 보면서 소수 회만 하고, 검색 POST를 무한 재시도하지 마.
문제가 생기면 원인을 조사하고 수정·테스트를 이어서 진행해.

가상 fixture 성공과 실제 계정 성공을 구분하고,
환산주스탯 템환산과 현재 독립 교체 모델을 구분해.
실제 30분 후 새 매물이 없으면 조회 성공·신규 0건으로 보고해.

결과는 VALIDATION.md에 커밋·환경·실제 성공 단계·실패 단계·수정/근거를 기록하고 알려줘.
기존 Discord 봇과 운영 서버는 별개야.
작업 브랜치에서 진행하고, main push/병합/운영 배포는 이번 작업 범위에 포함하지 마.
```
