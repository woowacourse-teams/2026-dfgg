#!/usr/bin/env python3
"""v3 요청 파일(requests.json)을 행 수와 순서 그대로 v4 요청 파일(requests-v4.json)로 늘린다.

v3·v4를 같은 조건으로 비교하려고 v3 요청 1,000건에 v4에만 있는 값 세 가지를 채운다.
v3 키(allies·enemies·myChampion·purchasedItemIds)는 그대로 둔다 — v4는 이 값으로 같은 Core를 호출한다.

  inventoryItems : purchasedItemIds를 수량 1로 옮긴다. EXCLUDED_ITEM_IDS에 있는 id는 여기서만 뺀다
  gameTimeMs     : 구매 k개 → (5 + 4k)분. 구매가 늘수록 게임이 진행된 것으로 본다
  availableGold  : 0~3,000 난수. SEED를 고정해 언제 만들어도 같은 값이 나온다

저장소 루트에서 실행한다: python3 backend/ops/perf/k6/make-v4-requests.py
"""
import json
import random
from pathlib import Path

SEED = 246

# 운영 카탈로그에 없어 v4가 400으로 거절하는 보유 아이템 id. k6 validate 시나리오 로그에서 찾아 넣는다.
# inventoryItems에서만 빼고 purchasedItemIds는 v3와 같게 둔다 — Core 입력이 v3와 같아야 비교가 된다.
# 이 목록이 바뀌면 requests-v4.json의 sha256이 바뀌므로 그 전에 잰 v4 결과는 비교에서 뺀다.
EXCLUDED_ITEM_IDS: set[int] = set()

DATA_DIR = Path(__file__).resolve().parent / "data"
SOURCE = DATA_DIR / "requests.json"
OUTPUT = DATA_DIR / "requests-v4.json"
V4_KEYS = ("inventoryItems", "gameTimeMs", "availableGold")
MINUTE_MS = 60_000


def to_v4(index: int, row: dict, rng: random.Random) -> tuple[dict, int]:
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
        "inventoryItems": inventory,
        "gameTimeMs": (5 + 4 * len(purchased)) * MINUTE_MS,
        "availableGold": gold,
    }
    return v4, len(purchased) - len(inventory)


def main() -> None:
    rows = json.loads(SOURCE.read_text(encoding="utf-8"))
    rng = random.Random(SEED)
    converted = [to_v4(index, row, rng) for index, row in enumerate(rows, start=1)]
    # v3 파일과 같은 서식(한 줄에 한 행)이라 두 파일의 N번째 줄이 같은 요청이다.
    lines = ",\n".join(json.dumps(row, ensure_ascii=False) for row, _ in converted)
    OUTPUT.write_text(f"[\n{lines}\n]\n", encoding="utf-8")

    removed = [count for _, count in converted]
    print(f"rows={len(converted)}")
    print(f"excluded_ids={sorted(EXCLUDED_ITEM_IDS)}")
    print(f"rows_with_exclusion={sum(1 for count in removed if count)}")
    print(f"removed_items={sum(removed)}")
    print(f"output={OUTPUT}")


if __name__ == "__main__":
    main()
