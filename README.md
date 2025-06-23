# Discord Music Bot

이 프로젝트는 Discord에서 음악 재생 기능을 제공하는 챗봇입니다. Java, JDA(Java Discord API), LavaPlayer 라이브러리를 사용하여 구현되었습니다.

---

## 기능 및 명령어

* **음악 재생** (`!play <URL 또는 검색어>`, `노래 <URL 또는 검색어>`)
* **음악 정지 및 큐 비우기** (`!stop`, `정지`)
* **곡 스킵** (`!skip`, `스킵`)
* **음성 채널 입장** (`!join`, `들어와`, `나와`)
* **음성 채널 퇴장** (`!leave`, `퇴장`, `나가`)
* **재생 목록 확인** (`!list`)
* **대기열 초기화** (`!extract`)

---

## 프로젝트 구조

### 메인 클래스 (`sp1.Main`)

* JDA 초기화
* Discord API에 연결 및 봇 실행

### 리스너 클래스 (`sp1.Listeners`)

* 사용자의 메시지를 처리하여 명령을 인식
* 각 명령에 따라 적절한 기능 호출
* 명령별 예외 처리 및 사용자 피드백 제공

### 오디오 기능 패키지 (`sp1.audio`)

#### PlayerManager

* 싱글톤으로 구현되어 전역으로 오디오 플레이어 관리
* LavaPlayer를 사용하여 음악 로딩 및 재생 제어
* Youtube를 포함한 다양한 소스에서 오디오 데이터를 받아 처리

#### GuildMusicManager

* 길드(서버)마다 고유한 오디오 플레이어와 스케줄러 관리
* 길드의 음성 채널로 오디오 데이터를 전송

#### TrackScheduler

* 음악 트랙의 재생 및 큐 관리
* 트랙 종료 시 자동으로 다음 트랙 재생
* 현재 큐의 목록 확인 및 큐 초기화 기능 제공

#### AudioPlayerSendHandler

* 오디오 프레임을 Discord 음성 채널로 전송
* 오디오 데이터의 Opus 인코딩을 관리하여 음성 품질 유지

---

## 개발 환경 설정

### 요구 사항

* Java 21 이상
* Discord 봇 토큰
* Maven 또는 Gradle

### 의존성 관리

```xml
<!-- JDA -->
<dependency>
    <groupId>net.dv8tion</groupId>
    <artifactId>JDA</artifactId>
    <version>5.0.0-beta.20</version>
</dependency>

<!-- LavaPlayer -->
<dependency>
    <groupId>com.sedmelluq</groupId>
    <artifactId>lavaplayer</artifactId>
    <version>1.3.78</version>
</dependency>
```

### 실행 방법

1. 봇 토큰을 `Main.java` 파일에 설정합니다.
2. 프로젝트 빌드 및 실행:

```
mvn clean install
java -jar your-bot.jar
```

---

## 확장 가이드

### 새로운 명령어 추가

* `Listeners.java` 클래스에 새 명령어 조건을 추가하고, 처리 메서드를 구현합니다.

### 음악 소스 확장

* `PlayerManager.java`의 `AudioPlayerManager` 인스턴스에 추가적인 오디오 소스 매니저를 등록합니다.

### 추가 기능 개발

* 음악 재생 중 반복, 재생 목록 저장/불러오기, 권한 관리 등의 기능을 개발하여 `TrackScheduler` 및 관련 클래스를 확장할 수 있습니다.

---

## 기여 안내

이 프로젝트는 오픈 소스로, 자유롭게 Fork 하여 Pull Request를 제출할 수 있습니다. 언제나 환영합니다!
