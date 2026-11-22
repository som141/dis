"""Generate deterministic SVG summaries; Mermaid files retain the detailed graph model."""
from pathlib import Path
from html import escape
import sys

ROOT = Path(__file__).resolve().parents[2] / 'docs' / 'diagrams'
COLORS = {'app': ('#eef5ff', '#497cac'), 'maple': ('#fff5e4', '#b68436'), 'edge': ('#f0ecfa', '#8570b1'), 'data': ('#edf6ef', '#648c70'), 'ext': ('#f5f7fa', '#8895a2')}

def card(x, y, w, title, lines, kind='app', h=100):
    fill, stroke = COLORS[kind]
    rows = ''.join(f'<text x="{x+18}" y="{y+54+i*22}" class="sub">{escape(line)}</text>' for i, line in enumerate(lines))
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="12" fill="{fill}" stroke="{stroke}" stroke-width="1.8"/><text x="{x+18}" y="{y+29}" class="title">{escape(title)}</text>{rows}'

def path(d, label='', x=0, y=0, both=False, dashed=False):
    start = ' marker-start="url(#back)"' if both else ''
    dash = ' stroke-dasharray="6 5"' if dashed else ''
    result = f'<path d="{d}" fill="none" stroke="#748699" stroke-width="2.2" marker-end="url(#arrow)"{start}{dash}/>'
    if label:
        width = max(len(label) * 13, 75)
        result += f'<rect x="{x-width/2}" y="{y-16}" width="{width}" height="23" rx="5" fill="white"/><text x="{x}" y="{y}" text-anchor="middle" class="label">{escape(label)}</text>'
    return result

def svg(body, height, title):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="1400" height="{height}" viewBox="0 0 1400 {height}" role="img" aria-label="{escape(title)}">
<title>{escape(title)}</title><defs>
<marker id="arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0 0 L8 4 L0 8" fill="#748699"/></marker>
<marker id="back" markerWidth="8" markerHeight="8" refX="1" refY="4" orient="auto"><path d="M8 0 L0 4 L8 8" fill="#748699"/></marker>
<style>text{{font-family:'Malgun Gothic','Noto Sans CJK KR',Arial,sans-serif;fill:#203247}} .title{{font-size:22px;font-weight:700}} .sub{{font-size:17px}} .label{{font-size:16px}} .heading{{font-size:16px;font-weight:700;fill:#64758a;letter-spacing:1px}}</style>
</defs><rect width="1400" height="{height}" fill="white"/>{body}</svg>\n'''

def overview():
    b = ['<text x="30" y="28" class="heading">음악 · 주식: Discord 명령과 비동기 결과</text>']
    b += [card(30,130,180,'Discord',['명령 · 응답 · DM'],'ext'), card(270,130,230,'Spring Gateway',['명령 수신 · OAuth','응답 · 개인 DM']), card(560,130,190,'RabbitMQ',['command / result']), card(800,45,230,'Audio Node',['길드별 player · queue','Discord 음성 전송']), card(800,215,230,'Stock Node',['시세 · 거래 · 계좌','시즌 · 랭킹']), card(1090,45,280,'YouTube / cipher',['음원 조회 · 서명 해독'],'ext'),card(1090,215,280,'Finnhub',['주식 시세 공급'],'ext')]
    b += [path('M210 180 H270',both=True),path('M500 180 H560',both=True),path('M750 165 H770 V95 H800',both=True),path('M750 195 H770 V265 H800',both=True),path('M1030 95 H1090'),path('M1030 265 H1090')]
    b += ['<text x="30" y="435" class="heading">메이플: 공개 웹 진입점과 사용자별 검색</text>']
    b += [card(30,470,180,'웹 사용자',['Discord 연결 후','공식 넥슨 로그인'],'ext'),card(270,470,230,'maple-edge',['신뢰한 HTTPS 터널','프록시에서 TLS 종료'],'edge'),card(560,470,230,'Maple Portal',['Node · 검색 · 계산','캐릭터별 감시'],'maple'),card(840,470,230,'Chromium',['사용자별 프로필','공식 로그인 · 검색'],'maple'),card(1130,470,240,'Nexon',['로그인 · 경매장','캐릭터 스탯 API'],'ext')]
    b += [path('M210 520 H270','HTTPS',240,506),path('M500 520 H560','웹 API',530,506),path('M790 520 H840'),path('M1070 520 H1130'),path('M385 470 V230','OAuth callback',385,345),path('M450 230 V375 H675 V470','내부 HTTP · 세션 / 발송함',675,363,both=True)]
    b += [path('M735 570 V610 H1250 V570','Portal → 캐릭터 API (서비스 키)',990,600,dashed=True)]
    b += ['<rect x="30" y="665" width="1340" height="155" rx="14" fill="#fafcfb" stroke="#b5c6bc"/><text x="50" y="690" class="heading">저장소와 소유 앱 · 공개 HTTPS에 연결하지 않음</text>']
    b += [card(50,710,400,'Redis',['Gateway pending · Audio 상태','Stock 시세·랭킹 캐시'],'data',h=90),card(485,710,420,'PostgreSQL · 기존 인스턴스',['Stock → public 계좌·거래 원장','Portal → maple 계정·작업·발송함'],'data',h=90),card(940,710,410,'프로필 볼륨',['Chromium → 사용자별 로그인 쿠키','DB와 별도 보관·백업'],'data',h=90)]
    return svg(''.join(b),845,'DIS 전체 구조: Spring 음악·주식과 Node 메이플 포털')

def boundary():
    b = ['<text x="30" y="30" class="heading">공개 요청 → 신뢰한 프록시 → 인증·소유권 검사 → 내부 데이터</text>']
    b += [card(30,80,220,'웹 사용자',['공개 HTTPS 요청'],'ext'),card(315,80,280,'TLS 프록시 / 터널',['Host·scheme 전달','호스트 loopback / 내부망'],'edge'),card(660,80,245,'maple-edge',['공개 웹 라우팅','/internal/ → 404'],'edge')]
    b += [path('M250 130 H315','HTTPS',282,117),path('M595 130 H660'),card(995,40,360,'Gateway',['/maple/connect · OAuth callback','서명·만료·Discord 신원 일치']),card(995,215,360,'Maple Portal',['세션·Origin·CSRF·소유권 검사','Gateway 내부 API는 별도 키 인증'],'maple'),path('M905 110 H945 V90 H995'),path('M905 150 H945 V265 H995'),path('M1180 140 V215','내부 HTTP',1180,184,both=True)]
    b += ['<rect x="30" y="370" width="1325" height="140" rx="12" fill="#f7f9fb" stroke="#b5c2cf"/><text x="50" y="402" class="title">운영자 전용 · 기본 호스트 loopback</text><text x="50" y="438" class="sub">DB · Redis · RabbitMQ · Actuator · Grafana · Prometheus · Loki · Alloy · exporters</text><text x="50" y="473" class="sub">공개 웹 라우팅 없음. 원격 점검은 인증된 SSH 포트 전달 등으로 접근. 실제 방화벽·계정 권한은 별도 확인.</text>']
    return svg(''.join(b),535,'공개 HTTPS 진입점과 내부 API 및 관리 포트의 경계')

for name, content in [('system-overview', overview()), ('public-boundary', boundary())]:
    target = ROOT / f'{name}.svg'
    if '--check' in sys.argv:
        if not target.exists() or target.read_text(encoding='utf-8') != content:
            raise SystemExit(f'{target.name}: regenerate with python ops/docs/render-diagrams.py')
    else:
        target.write_text(content, encoding='utf-8')
print('Architecture SVGs verified.' if '--check' in sys.argv else 'Architecture SVGs generated.')
