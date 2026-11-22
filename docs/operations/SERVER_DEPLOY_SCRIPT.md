# 배포 안내 문서의 이동

현재 배포는 **GitHub Release 게시 → 서버 systemd timer의 pull → 체크섬 확인·백업·마이그레이션 → Compose 전환**이다. CI가 SSH로 서버에 들어가 `.env`를 업로드하는 방식은 사용하지 않는다.

- [현재 서버 설치·배포 절차](OUTBOUND_ONLY_DEPLOY.md)
- [메이플 HTTPS·OAuth·DB 설정](MAPLE_DISCORD_SETUP.md)
- [비밀값·공개 포트·로그 점검](SECURITY.md)
- [배포 후 장애 대응](OPERATIONS_RUNBOOK.md)
- [음원 인증·개발 설정](LOCAL_DEVELOPMENT.md)

이 주소에 있던 SSH 업로드·CI Secret 기반 환경 파일 생성 절차는 현재 설정과 달라 실행 안내에서 제거했다. 당시 구성의 맥락은 [이전 커밋의 문서](https://github.com/som141/dis/blob/29ebb0a04b5ae1516ab6981dffe3d422c302d10e/docs/operations/SERVER_DEPLOY_SCRIPT.md)에서 확인할 수 있다. 과거 명령을 현재 운영 서버에 그대로 적용하지 않는다.
