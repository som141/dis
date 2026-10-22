# Gradle wrapper와 YouTube 의존성 공급

[wrapper/](wrapper/)는 Gradle 8.8 실행 파일을 관리한다. [youtube-source-releases.gradle](youtube-source-releases.gradle)은 기존 youtube-source **1.18.2** 두 모듈을 공식 GitHub release에서 받고 SHA-256을 검증한다. 루트 [build.gradle](../build.gradle)이 이 설정을 모든 프로젝트에 적용한다.

## Maven 대신 공식 릴리스를 사용하는 이유

2026-10-22에 `maven.lavalink.dev/releases`의 `common-1.18.2.pom`, `v2-1.18.2.pom` URL이 XML 대신 `xl.meta` 폴더 목록 HTML을 반환하는 문제를 재현했다. Gradle은 `Already seen doctype`와 함께 compileClasspath 해석에 실패했다.

라이브러리를 업그레이드하거나 테스트를 생략하는 대신, 같은 버전의 [공식 릴리스](https://github.com/lavalink-devs/youtube-source/releases/tag/1.18.2) 파일을 사용한다. 전용 Ivy 저장소는 `dev.lavalink.youtube:common:1.18.2`, `dev.lavalink.youtube:v2:1.18.2`만 선택한다. 다른 의존성은 기존 저장소 설정을 따른다. JAR는 저장소에 직접 커밋하지 않고 Gradle 캐시에 보관한다.

| 파일 | 공식 GitHub asset ID | SHA-256 |
| --- | --- | --- |
| `youtube-common-1.18.2.jar` | `490926661` | `22f56b8632aec8b081fb7ab6d9adad012cd2a1b7a5f77167d308a6f1b3f27a35` |
| `youtube-v2-1.18.2.jar` | `490926621` | `0f328732150cc2b2ea791b180b32e1eae520944603cccecd4002af9a0c3f39d6` |

이 값은 공식 GitHub release API가 제공하는 asset digest와 대조했다. `verifyYoutubeSources`가 common-core·Gateway·audio의 Java 컴파일·테스트·JAR 패키징 전에 실행되며, 바이트가 다르면 빌드를 중단한다. stock만 빌드할 때는 YouTube 검사를 요구하지 않는다. TLS 검증을 끄거나 현재 다운로드의 해시로 기대값을 자동 갱신하지 않는다.

## Maven metadata의 런타임 의존성 보존

릴리스 JAR에는 Maven POM을 함께 받는 경로가 없으므로, 아래 원본 빌드 파일을 근거로 component metadata를 지정한다. 기준은 release tag가 가리키는 commit `158726dc7d570e8ff32512d6ab15736d561040b7`이다.

- [common/build.gradle.kts](https://github.com/lavalink-devs/youtube-source/blob/158726dc7d570e8ff32512d6ab15736d561040b7/common/build.gradle.kts): `org.mozilla:rhino-engine:1.7.15`, `com.grack:nanojson:1.7`.
- [v2/build.gradle.kts](https://github.com/lavalink-devs/youtube-source/blob/158726dc7d570e8ff32512d6ab15736d561040b7/v2/build.gradle.kts): 같은 의존성과 `common:1.18.2`.
- [settings.gradle.kts](https://github.com/lavalink-devs/youtube-source/blob/158726dc7d570e8ff32512d6ab15736d561040b7/settings.gradle.kts): 위 외부 라이브러리의 버전.

upstream의 compileOnly LavaPlayer·SLF4J 등은 기존 common-core 의존성으로 공급한다. 의존성을 받을 경로만 바꾸고 음악 처리 코드는 변경하지 않는다.

## 확인과 갱신

저장소 루트에서:

```bash
bash ./gradlew verifyYoutubeSources --no-daemon
bash ./gradlew clean test bootJarAll --no-daemon
```

Windows는 `bash ./gradlew` 대신 `.\gradlew.bat`를 사용한다. 버전을 바꿀 때는 [common-core 선언](../modules/common-core/build.gradle), 전용 저장소의 버전 필터, component metadata, 검증 좌표·공식 digest를 함께 갱신하고 전체 테스트를 다시 실행한다. Maven 공급 경로를 복구할 때도 실제 POM·JAR 응답과 빌드를 검증한 뒤 전환한다.

[테스트 가이드](../docs/operations/CI_TESTING.md) · [프로젝트 README](../README.md)
