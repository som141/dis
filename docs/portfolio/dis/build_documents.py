"""Build standalone HTML from the reviewable Markdown originals.

python -m pip install -r requirements-render.txt
python build_documents.py

Open index.html or resume.html in a browser and print to PDF.
No API keys, browser profiles, or network requests are used by this script.
"""
from html import escape
from pathlib import Path
import re

from markdown_it import MarkdownIt

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parents[2]
BRANCH = "portfolio/dis-case-study"
CODE_COMMIT = "32df09c84365726d5c110472738f913bdef25fae"
md = MarkdownIt("commonmark").enable("table")

FLOW = """<ol class="flow" aria-label="명령에서 사용자 답변까지의 흐름">
<li><span>01</span><strong>명령 입력</strong><small>Discord 사용자</small></li>
<li><span>02</span><strong>답변 준비</strong><small>돌려줄 위치를 먼저 기록</small></li>
<li><span>03</span><strong>작업 처리</strong><small>음악 또는 모의투자</small></li>
<li><span>04</span><strong>결과 안내</strong><small>원래 요청에 답변</small></li></ol>"""
old_fence = md.renderer.rules["fence"]


def fence(tokens, idx, options, env):
    if tokens[idx].info.strip() == "mermaid":
        return FLOW
    return old_fence(tokens, idx, options, env)


md.renderer.rules["fence"] = fence


def render(source):
    tokens = md.parse(source)
    for token in tokens:
        for child in token.children or []:
            if child.type != "link_open":
                continue
            href = child.attrGet("href") or ""
            if "://" in href or href.startswith(("#", "mailto:")):
                continue
            path_text, _, fragment = href.partition("#")
            path = (ROOT / path_text).resolve()
            relative = path.relative_to(REPO).as_posix()
            ref = BRANCH if path.is_relative_to(ROOT) else CODE_COMMIT
            url = f"https://github.com/som141/dis/blob/{ref}/{relative}"
            if fragment:
                url += "#" + fragment
            child.attrSet("href", url)
    return md.renderer.render(tokens, md.options, {})


CSS = """
:root{--ink:#202c35;--muted:#62717b;--accent:#116c6e;--paper:#fff;--ground:#f3f2ed;--line:#dce4e1;--tint:#eff6f3}
*{box-sizing:border-box}html{scroll-behavior:smooth}body{margin:0;background:var(--ground);color:var(--ink);font-family:'Noto Sans CJK KR','Malgun Gothic',sans-serif;font-size:15px;line-height:1.85;word-break:keep-all;overflow-wrap:break-word}
a{color:var(--accent);text-underline-offset:4px}a:hover{text-decoration-thickness:2px}button,a:focus-visible{outline-offset:4px}button{font:inherit;cursor:pointer}
.topbar{position:sticky;top:0;z-index:5;display:flex;justify-content:space-between;align-items:center;gap:20px;background:#f3f2edf5;border-bottom:1px solid var(--line);padding:15px 6vw;font-size:13px;backdrop-filter:blur(8px)}.brand{font-weight:700;letter-spacing:.05em}.toplinks{display:flex;gap:20px;align-items:center;flex-wrap:wrap}.toplinks a{text-decoration:none}.print-button{background:var(--ink);color:white;border:0;border-radius:5px;padding:8px 15px}
.layout{max-width:1230px;margin:40px auto;display:grid;grid-template-columns:190px minmax(0,860px);gap:34px;padding:0 26px}.sidebar{position:sticky;top:100px;align-self:start;font-size:12px}.sidebar p{color:var(--muted);margin:0 0 18px}.sidebar a{display:block;color:var(--muted);text-decoration:none;padding:8px 0}.sidebar a:hover{color:var(--accent)}.sidebar .nav-caption{font-size:10px;letter-spacing:.15em;font-weight:700;color:var(--accent)}
.document-sheet{background:var(--paper);padding:40px 46px;margin:0 0 30px;border:1px solid var(--line);border-radius:8px;box-shadow:0 8px 22px #23382c05;scroll-margin-top:90px}.folio{display:flex;justify-content:space-between;gap:18px;border-bottom:1px solid var(--line);padding-bottom:15px;margin-bottom:23px;font-size:11px;font-weight:700;letter-spacing:.12em;color:var(--accent)}h1{font-size:34px;line-height:1.45;letter-spacing:-.06em;margin:10px 0 18px}h2{font-size:23px;line-height:1.55;letter-spacing:-.035em;margin:28px 0 14px}h3{font-size:16px;line-height:1.7;margin:23px 0 9px;color:var(--accent)}p{margin:11px 0}strong{font-weight:700}li{margin:5px 0}ul,ol{padding-left:23px}.lede{font-size:17px}.document-sheet>h2:first-of-type{margin-top:0}hr{border:0;border-top:1px solid var(--line);margin:24px 0}
table{width:100%;border-collapse:collapse;margin:20px 0;font-size:12px;line-height:1.75}th,td{text-align:left;padding:10px 12px;border-bottom:1px solid var(--line);vertical-align:top}th{background:var(--tint);font-weight:700}th:first-child,td:first-child{width:26%}blockquote{margin:20px 0;padding:12px 18px;border-left:3px solid var(--accent);background:var(--tint)}code{font-family:ui-monospace,monospace;font-size:.88em;background:var(--tint);padding:1px 4px;border-radius:3px}.flow{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;list-style:none;padding:0;margin:25px 0}.flow li{border:1px solid var(--line);border-radius:5px;padding:14px 9px;background:var(--tint);margin:0}.flow span{display:block;font-size:11px;color:var(--accent);font-weight:700}.flow strong{display:block;margin:3px 0;font-size:13px}.flow small{display:block;font-size:10px;line-height:1.6;color:var(--muted)}.ending{margin:35px 0 55px;text-align:center;font-size:12px;color:var(--muted)}.review-link{border-top:1px solid var(--line);padding-top:18px;margin-top:25px;font-size:12px;color:var(--muted)}
.resume-layout{max-width:840px;margin:35px auto;padding:0 20px}.resume-sheet{padding:35px 42px}.resume-sheet h1{font-size:30px;margin-top:0}.resume-sheet h2{font-size:18px;border-bottom:1px solid var(--line);padding-bottom:5px;margin:22px 0 10px}.resume-sheet p,.resume-sheet li{font-size:13px;line-height:1.8}.resume-sheet ul{margin:10px 0}.resume-sheet table{font-size:11px}
@media(max-width:850px){.layout{display:block;max-width:800px;margin:20px auto;padding:0 14px}.sidebar{position:static;display:flex;gap:14px;overflow:auto;white-space:nowrap;margin-bottom:16px}.sidebar p,.sidebar .nav-caption{display:none}.sidebar a{padding:8px 0}.document-sheet{padding:28px 25px}h1{font-size:27px}h2{font-size:21px}.topbar{padding:12px 18px;gap:12px}.toplinks{gap:12px;font-size:11px}.brand{font-size:11px}.resume-layout{padding:0 12px}.resume-sheet{padding:28px 24px}}
@media(max-width:480px){body{font-size:14px}.document-sheet{padding:24px 19px}.flow{grid-template-columns:repeat(2,minmax(0,1fr))}table{font-size:11px}th,td{padding:8px}.toplinks .pdf-link{display:none}.folio{font-size:9px}h1{font-size:24px}}
@page{size:A4;margin:15mm 15mm 17mm}
@media print{html{scroll-behavior:auto}body{background:white;font-size:10pt;line-height:1.68;color:#202c35;-webkit-print-color-adjust:exact;print-color-adjust:exact}.topbar,.sidebar,.ending{display:none}.layout,.resume-layout{display:block;max-width:none;margin:0;padding:0}.document-sheet{border:0;box-shadow:none;border-radius:0;margin:0;padding:0;break-after:page;scroll-margin:0}.document-sheet:last-child{break-after:auto}.folio{font-size:8pt;padding-bottom:8px;margin-bottom:16px}h1{font-size:23pt;line-height:1.4;margin:5px 0 14px}h2{font-size:16pt;line-height:1.5;margin:21px 0 10px;break-after:avoid}h3{font-size:11pt;margin:15px 0 6px;break-after:avoid}p{margin:8px 0;orphans:3;widows:3}table{font-size:8.6pt;line-height:1.65;margin:13px 0}tr{break-inside:avoid}th,td{padding:7px 9px}a{color:inherit;text-decoration:none}li{break-inside:avoid}.flow{grid-template-columns:repeat(4,minmax(0,1fr));margin:20px 0}.flow li{padding:10px 8px}.flow strong{font-size:9pt}.flow small{font-size:7.8pt}.flow span{font-size:8pt}.review-link{font-size:8pt;margin-top:18px;padding-top:12px}.resume-sheet{break-after:auto}.resume-sheet h1{font-size:23pt}.resume-sheet h2{font-size:12pt;margin:13px 0 6px;padding-bottom:4px;break-after:avoid}.resume-sheet p,.resume-sheet li{font-size:9.3pt;line-height:1.58}.resume-sheet p{margin:5px 0}.resume-sheet ul{margin:5px 0;padding-left:18px}.resume-sheet li{margin:2px 0}.resume-sheet table{font-size:8.3pt;margin:8px 0}.resume-sheet th,.resume-sheet td{padding:5px 8px}.resume-sheet .folio{display:none}}
"""


def header(kind):
    pdf = "YOON_YOUNGJU_RESUME.pdf" if kind == "RESUME" else "DIS_PORTFOLIO.pdf"
    return f"""<header class="topbar"><span class="brand">윤영주 / {kind}</span>
<nav class="toplinks" aria-label="문서 선택"><a href="index.html">DIS 포트폴리오</a><a href="resume.html">이력서</a>
<a class="pdf-link" href="{pdf}">PDF</a><button class="print-button" type="button" id="print">인쇄 / 저장</button></nav></header>"""


def html_document(title, body):
    return f"""<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="description" content="윤영주의 DIS 프로젝트 포트폴리오와 이력서. 실제 사용과 운영 문제 해결을 기록했습니다.">
<title>{escape(title)}</title><style>{CSS}</style></head><body>{body}
<script>document.getElementById('print').addEventListener('click',()=>window.print());</script></body></html>"""


def sheet(name, label, source):
    return f'<section class="document-sheet" id="{name}"><div class="folio"><span>DIS / {label}</span><span>윤영주 · 2026.10</span></div>{render(source)}</section>'


def build():
    portfolio = (ROOT / "PORTFOLIO.md").read_text()
    prefix, *sections = re.split(r"(?=^## \d+\.)", portfolio, flags=re.M)
    assert len(sections) == 8, "Update chapter mapping when editing portfolio structure"
    challenges = (ROOT / "CHALLENGES.md").read_text().split("\n", 1)[1]
    challenges = re.sub(r"^## ", "### ", challenges, flags=re.M)
    challenges = challenges.split("[상세 이야기]")[0]
    pages = [
        sheet("overview", "01 · PROJECT", prefix + sections[0] + sections[1]),
        sheet("structure", "02 · OVERVIEW", sections[2] + "\n## 핵심 챌린지 세 가지\n" + challenges),
        sheet("music", "03 · MUSIC", sections[3]),
        sheet("response", "04 · RESPONSE", sections[4]),
        sheet("delivery", "05 · DELIVERY", sections[5]),
        sheet("learning", "06 · LEARNING", sections[6] + sections[7]),
    ]
    sidebar = """<aside class="sidebar"><p class="nav-caption">CONTENTS</p><p>사용 장면에서 시작해<br>확인한 결과까지.</p>
<a href="#overview">01 프로젝트 소개</a><a href="#structure">02 구조와 챌린지</a><a href="#music">03 음악이 나오지 않을 때</a>
<a href="#response">04 답변이 돌아오는 순서</a><a href="#delivery">05 빌드·배포 문제</a><a href="#learning">06 결과와 배운 점</a></aside>"""
    body = header("PROJECT PORTFOLIO") + '<div class="layout">' + sidebar + '<main>' + "".join(pages) + '</main></div><footer class="ending">윤영주 · DIS · 작성 기준 2026.10.27</footer>'
    (ROOT / "index.html").write_text(html_document("윤영주 · DIS 프로젝트 포트폴리오", body))
    resume = render((ROOT / "RESUME.md").read_text())
    body = header("RESUME") + '<main class="resume-layout"><article class="document-sheet resume-sheet">' + resume + '</article></main><footer class="ending">공개 GitHub 이력서와 프로젝트 기록을 바탕으로 작성한 개정 초안 · 2026.10.27</footer>'
    (ROOT / "resume.html").write_text(html_document("윤영주 · 이력서", body))
    print("Built index.html and resume.html from Markdown originals.")


if __name__ == "__main__":
    build()
