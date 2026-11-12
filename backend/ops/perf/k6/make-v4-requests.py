#!/usr/bin/env python3
"""v3 요청 파일(requests.json)을 행 수와 순서 그대로 v4 요청 파일(requests-v4.json)로 늘린다.

v3 요청 1,000건에 v4에만 있는 값 세 가지를 채운다. 챔피언과 구매 목록은 v3 요청 그대로이고,
챔피언 이름만 데스크톱 앱이 보내는 형식(영어)으로 바꾼다.

  champion name  : 한글 이름 → Data Dragon en_US champion.name. 앱(payload.ts의 toEnglishName)과 같은 값이다.
                   한글 이름이면 백엔드가 riotKey 조회에 실패하고 이름으로 한 번 더 찾아, 챔피언 조회 비용을
                   실제보다 크게 잰다(#277). 대응표에 없는 이름이 있으면 파일을 만들지 않는다
  inventoryItems : purchasedItemIds를 수량 1로 옮긴다. EXCLUDED_ITEM_IDS에 있는 id는 여기서만 뺀다
  gameTimeMs     : 구매 k개 → (5 + 4k)분. 구매가 늘수록 게임이 진행된 것으로 본다
  availableGold  : 0~3,000 난수. SEED를 고정해 언제 만들어도 같은 값이 나온다

저장소 루트에서 실행한다(Data Dragon을 받으므로 인터넷이 필요하다): python3 backend/ops/perf/k6/make-v4-requests.py
"""
import json
import random
import urllib.request
from pathlib import Path

SEED = 246

# 이름 대응표를 받을 Data Dragon 버전. 운영 패치로 고정해 언제 만들어도 같은 파일이 나오게 한다.
DDRAGON_VERSION = "16.21.1"
DDRAGON_CHAMPIONS = "https://ddragon.leagueoflegends.com/cdn/{version}/data/{locale}/champion.json"
TEAM_KEYS = ("allies", "enemies")

# 운영 카탈로그에 없어 v4가 400으로 거절하는 보유 아이템 id. k6 validate 시나리오 로그에서 찾아 넣는다.
# inventoryItems에서만 빼고 purchasedItemIds는 v3와 같게 둔다 — 구매 목록까지 바꾸면 Core가 다른 상황을 추천한다.
# 이 목록이 바뀌면 requests-v4.json의 sha256이 바뀌므로 그 전에 잰 v4 결과는 비교에서 뺀다.
EXCLUDED_ITEM_IDS: set[int] = set()

DATA_DIR = Path(__file__).resolve().parent / "data"
SOURCE = DATA_DIR / "requests.json"
OUTPUT = DATA_DIR / "requests-v4.json"
V4_KEYS = ("inventoryItems", "gameTimeMs", "availableGold")
MINUTE_MS = 60_000


def champions(locale: str) -> dict:
    url = DDRAGON_CHAMPIONS.format(version=DDRAGON_VERSION, locale=locale)
    with urllib.request.urlopen(url, timeout=30) as response:
        return json.load(response)["data"]


def english_names() -> dict[str, str]:
    """한글 이름 → 영어 이름. 같은 챔피언 id로 두 언어를 잇는다."""
    korean = champions("ko_KR")
    english = champions("en_US")
    return {korean[champion_id]["name"]: english[champion_id]["name"] for champion_id in korean}


def with_english_name(index: int, champion: dict, names: dict[str, str]) -> dict:
    name = champion["name"]
    if name not in names:
        raise SystemExit(f"{index}번째 행의 챔피언 이름이 Data Dragon {DDRAGON_VERSION} 대응표에 없다: {name}")
    return {**champion, "name": names[name]}


def to_v4(index: int, row: dict, rng: random.Random, names: dict[str, str]) -> tuple[dict, int]:
    purchased = row["purchasedItemIds"]
    if len(purchased) != len(set(purchased)):
        raise SystemExit(f"{index}번째 행의 purchasedItemIds에 중복이 있다(v4는 400): {purchased}")
    if any(key in row for key in V4_KEYS):
        raise SystemExit(f"{index}번째 행에 이미 v4 키가 있다: {row}")
    # 제외 목록과 상관없이 행마다 한 번 뽑아, 목록이 바뀌어도 골드는 그대로다.
    gold = rng.randint(0, 3000)
    inventory = [{"itemId": item_id, "quantity": 1} for item_id in purchased if item_id not in EXCLUDED_ITEM_IDS]
    v4 = {
        **row,
        "myChampion": with_english_name(index, row["myChampion"], names),
        **{team: [with_english_name(index, champion, names) for champion in row[team]] for team in TEAM_KEYS},
        "inventoryItems": inventory,
        "gameTimeMs": (5 + 4 * len(purchased)) * MINUTE_MS,
        "availableGold": gold,
    }
    return v4, len(purchased) - len(inventory)


def main() -> None:
    rows = json.loads(SOURCE.read_text(encoding="utf-8"))
    rng = random.Random(SEED)
    names = english_names()
    converted = [to_v4(index, row, rng, names) for index, row in enumerate(rows, start=1)]
    # v3 파일과 같은 서식(한 줄에 한 행)이라 두 파일의 N번째 줄이 같은 요청이다.
    lines = ",\n".join(json.dumps(row, ensure_ascii=False) for row, _ in converted)
    OUTPUT.write_text(f"[\n{lines}\n]\n", encoding="utf-8")

    removed = [count for _, count in converted]
    print(f"rows={len(converted)}")
    print(f"ddragon_version={DDRAGON_VERSION}")
    print(f"excluded_ids={sorted(EXCLUDED_ITEM_IDS)}")
    print(f"rows_with_exclusion={sum(1 for count in removed if count)}")
    print(f"removed_items={sum(removed)}")
    print(f"output={OUTPUT}")


if __name__ == "__main__":
    main()
