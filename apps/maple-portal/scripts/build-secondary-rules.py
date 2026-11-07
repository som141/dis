"""Build exact secondary-weapon/job rules from the catalog and Nexon updates.

Run from apps/maple-portal: python scripts/build-secondary-rules.py
The transfer list is not a complete wearability list. We use its Astra rows
and named job-specific older weapons, while excluding ordinary shared shields.
The catalog's explicit secondary-weapon subtypes cover other older variants.
"""

import json
from pathlib import Path
from urllib.request import Request, urlopen

from bs4 import BeautifulSoup


ROOT = Path(__file__).resolve().parents[1]
SOURCE = "https://maplestory.nexon.com/News/Update/794"
LETE_SOURCE = "https://maplestory.nexon.com/News/Update/807"
SUBTYPE_JOBS = {
    "Arrow Fletching": ["보우마스터"], "Arrowhead": ["와일드헌터"],
    "Ballast": ["캡틴"], "Bow Thimble": ["신궁"],
    "Card": ["팬텀"], "Charges": ["블래스터"],
    "Charm": ["나이트로드"], "Chess Piece": ["키네시스"],
    "Core Controller": ["제논"], "Dagger Scabbard": ["섀도어"],
    "Document": ["에반"], "Fox Marble": ["은월"],
    "Iron Chain": ["다크나이트"],
    "Jewel": ["소울마스터", "플레임위자드", "윈드브레이커", "나이트워커", "스트라이커"],
    "Magic Arrow": ["메르세데스"], "Magic Marble": ["배틀메이지"],
    "Magnum": ["메카닉"], "Mass": ["아란"],
    "Nova's Essence": ["카이저"], "Orb": ["루미너스"],
    "Path": ["아크"], "Powder Keg": ["캐논마스터"],
    "Relic": ["패스파인더"], "Rosary": ["팔라딘"],
    "Soul Ring": ["엔젤릭버스터"], "Transmitter": ["카데나"],
    "Wings": ["일리움"], "Wrist Band": ["바이퍼"],
}
SPELLBOOK_JOBS = {"적녹의 서": "아크메이지(불,독)", "청은의 서": "아크메이지(썬,콜)", "백금의 서": "비숍"}
JOB_ALIASES = {"캐논 슈터": "캐논마스터", "데몬 슬레이어": "데몬슬레이어", "데몬 어벤져": "데몬어벤져", "아크메이지(불, 독)": "아크메이지(불,독)", "아크메이지(썬, 콜)": "아크메이지(썬,콜)"}
# These ordinary shields can be worn by more jobs than the Astra transfer table
# lists. A transfer entry is evidence of one allowed job, not an exclusive rule.
SHARED_SHIELDS = {
    "피어리스 카이트 실드", "데이모스 워리어 실드", "블루 호플론",
    "바이올렛 호플론", "그린 호플론", "블루 드래곤 실드",
    "타임리스 카이트 실드", "피어리스 프렐류드",
    "데이모스 세이지 실드", "타임리스 프렐류드",
    "데이모스 다크니스 실드", "피어리스 리스트", "타임리스 리스트",
}


def main():
    catalog = json.loads((ROOT / "public/data/equipment.json").read_text(encoding="utf-8"))
    rules = {}
    for item in catalog["items"]:
        if item["category"] != "WEAPON_SUB":
            continue
        subtype = item["subtype"]
        if subtype == "Spellbook":
            jobs = [job for prefix, job in SPELLBOOK_JOBS.items() if item["name"].startswith(prefix) or item["name"].startswith("이볼빙 " + prefix)]
        else:
            jobs = SUBTYPE_JOBS.get(subtype)
        if not jobs:
            raise ValueError(f"Unmapped catalog secondary weapon: {item['name']} ({subtype})")
        rules.setdefault(item["name"], set()).update(jobs)

    request = Request(SOURCE, headers={"User-Agent": "Mozilla/5.0"})
    with urlopen(request, timeout=25) as response:
        soup = BeautifulSoup(response.read(), "html.parser")
    tables = [table for table in soup.find_all("table") if "전승 가능 아이템 목록" in table.get_text()]
    if len(tables) != 1:
        raise ValueError("Nexon transfer table not found uniquely")
    official_count = 0
    for row in tables[0].find_all("tr")[1:]:
        cells = [cell.get_text(" ", strip=True) for cell in row.find_all(["td", "th"])]
        if len(cells) != 3 or not cells[0].startswith("아스트라 "):
            continue
        jobs = [JOB_ALIASES.get(job.strip(), job.strip()) for job in cells[1].split(",")]
        # The comma in the two Archmage names is part of the job name.
        if cells[1].startswith("아크메이지("):
            jobs = [JOB_ALIASES.get(cells[1], cells[1])]
        if not all(jobs):
            raise ValueError(f"Missing Astra jobs: {cells}")
        rules.setdefault(cells[0], set()).update(jobs)
        for name in (part.strip() for part in cells[2].split(",")):
            if name and name not in SHARED_SHIELDS:
                rules.setdefault(name, set()).update(jobs)
        official_count += 1
    if official_count < 40:
        raise ValueError(f"Incomplete official secondary table: {official_count} rows")
    # Zero has no transfer entry, but the same update explicitly lists its Hourglass.
    if "아스트라 아워글라스" not in soup.get_text():
        raise ValueError("Zero Astra weapon not found in official update")
    rules.setdefault("아스트라 아워글라스", set()).add("제로")

    # Lete was released after update 794. Update 807 explicitly labels these
    # seven names as secondary weapons; do not infer from its wing equipment.
    lete_names = [
        "메리드 마법깃펜", "베스페르 마법깃펜", "크레푸스 마법깃펜",
        "녹스 마법깃펜", "이볼빙 녹스 마법깃펜", "블랙 마법깃펜",
        "아스트라 마법깃펜",
    ]
    with urlopen(Request(LETE_SOURCE, headers={"User-Agent": "Mozilla/5.0"}), timeout=25) as response:
        lete_text = BeautifulSoup(response.read(), "html.parser").get_text()
    for name in lete_names:
        if f"보조무기 : {name}" not in lete_text:
            raise ValueError(f"Lete secondary not found in official update: {name}")
        rules.setdefault(name, set()).add("레테")

    result = {"sources": [SOURCE, LETE_SOURCE], "catalogVersion": catalog["metadataVersion"], "jobsByName": {name: sorted(jobs) for name, jobs in sorted(rules.items())}}
    target = ROOT / "vendor/maple-auction/damage/secondaryWeaponRules.json"
    target.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(result['jobsByName'])} exact-name rules from {official_count} official rows and catalog secondary weapons")


if __name__ == "__main__":
    main()
