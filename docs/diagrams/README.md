# 아키텍처 그림

- [전체 구조](system-overview.svg) / [Mermaid 원본](system-overview.mmd): 음악·주식 메시징, 메이플 내부 HTTP, 외부 서비스·데이터 경계.
- [공개 경계](public-boundary.svg) / [Mermaid 원본](public-boundary.mmd): HTTPS 진입, 내부 API 차단, 관리 포트.

README는 GitHub의 Mermaid 지원 여부에 상관없이 읽을 수 있도록 SVG를 표시한다. 많은 연결선이 한 그림에 겹치지 않도록 SVG는 [Python 생성기](../../ops/docs/render-diagrams.py)로 배치를 고정하고, 상세 연결은 Mermaid 원본으로 보존한다. 두 표현의 책임·데이터 흐름을 함께 유지한다. 실제 호스트명·계정·키를 넣지 않는다.

주요 그림을 수정한 뒤 저장소 루트에서 실행한다. 글자 잘림·선 교차·한글 렌더링도 확인한다.

```bash
python ops/docs/render-diagrams.py
python ops/docs/render-diagrams.py --check
```

상세 Mermaid 연결 모델의 구문은 [공식 flowchart 안내](https://mermaid.js.org/syntax/flowchart.html)를 따른다. Node.js 24와 Chrome/Chromium에서 다음처럼 별도 임시 파일로 렌더할 수 있다. CLI 버전은 12.2.0으로 고정한다. 이미 설치한 Chrome을 쓸 때 `PUPPETEER_SKIP_DOWNLOAD=true`, `PUPPETEER_EXECUTABLE_PATH`를 해당 실행 파일 경로로 지정한다.

```bash
npx --yes @mermaid-js/mermaid-cli@12.2.0 -i docs/diagrams/system-overview.mmd -o /tmp/dis-overview.svg -c docs/diagrams/mermaid-config.json -b white
npx --yes @mermaid-js/mermaid-cli@12.2.0 -i docs/diagrams/public-boundary.mmd -o /tmp/dis-boundary.svg -c docs/diagrams/mermaid-config.json -b white
```

Windows에서는 `/tmp` 대신 `%TEMP%`에 해당하는 실제 경로를 사용한다. CI는 SVG 생성 일치와 Mermaid 렌더 가능성을 확인하고 로컬에서는 실제 출력 이미지를 확인한다. SVG에는 링크·스크립트·인증값을 넣지 않는다. 세부 인증 순서와 세트 계산 설명은 각 기준 문서에 두며 전체 그림에 모든 조건을 넣지 않는다.
