#!/usr/bin/env python3
"""Summarize diagnostic-only out-of-target-path purchases; never generate candidates."""
import argparse
from collections import Counter, defaultdict
import csv
import gzip
import hashlib
import json
from pathlib import Path
import statistics


def sha256(path):
    result = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            result.update(chunk)
    return result.hexdigest()


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + '\n')


def write_csv(path, rows, fields):
    with path.open('w', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


def accumulator():
    return {'queries': 0, 'cleanQueries': 0, 'matches': set(), 'linked': 0,
            'cleanLinked': 0, 'elapsed': [], 'futureItems': Counter()}


def add_purchase(bucket, row, item_id):
    bucket['queries'] += 1
    bucket['matches'].add(row['matchId'])
    clean = not row['bundleExclusionReasons']
    bucket['cleanQueries'] += clean
    linked = [c for c in row['subsequentCompletedItems']
              if c['evidence'] == 'RECIPE_MEMBERSHIP_ONLY' and item_id in c['relatedOutsideItemIds']]
    if linked:
        bucket['linked'] += 1
        bucket['cleanLinked'] += clean
        elapsed = linked[0]['elapsedSincePurchaseMs']
        if elapsed is not None:
            bucket['elapsed'].append(elapsed / 60000)
        for completed_id in {c['itemId'] for c in linked}:
            bucket['futureItems'][completed_id] += 1


def pattern_row(key, bucket, name):
    champion, role, target, item = key
    n = bucket['queries']
    return {'championId': champion, 'role': role, 'targetItemId': target, 'targetItemName': name(target),
            'outsideItemId': item, 'outsideItemName': name(item), 'queryCount': n,
            'matchCount': len(bucket['matches']), 'cleanBundleQueryCount': bucket['cleanQueries'],
            'laterRecipeLinkedQueryCount': bucket['linked'], 'laterRecipeLinkedShare': bucket['linked'] / n,
            'cleanLaterRecipeLinkedQueryCount': bucket['cleanLinked'],
            'cleanLaterRecipeLinkedShare': bucket['cleanLinked'] / bucket['cleanQueries'] if bucket['cleanQueries'] else None,
            'medianMinutesUntilFirstRelatedCompletion': statistics.median(bucket['elapsed']) if bucket['elapsed'] else None,
            'relatedCompletedItems': json.dumps(dict(sorted(bucket['futureItems'].items())), sort_keys=True)}


def analyze(args):
    args.out_dir.mkdir(parents=True, exist_ok=True)
    source = args.input_dir / 'out-of-target-path-purchases.jsonl.gz'
    export = json.loads((args.input_dir / 'candidate-scope-summary.json').read_text())
    catalog = json.loads(args.catalog.read_text())['data']
    name = lambda item: catalog.get(str(item), {}).get('name', str(item))
    patterns = defaultdict(accumulator)
    counts = Counter()
    exclusions = Counter()
    future_errors = Counter()
    examples = []
    seen = set()
    with gzip.open(source, 'rt') as stream:
        for line in stream:
            row = json.loads(line)
            assert row['status'] == 'OUT_OF_TARGET_PATH_PURCHASE'
            assert row['outsideTargetPathItems']
            assert row['queryId'] not in seen, row['queryId']
            seen.add(row['queryId'])
            counts['outsideQueries'] += 1
            counts['cleanBundleQueries'] += not row['bundleExclusionReasons']
            counts['missingChampionQueries'] += row['championId'] is None
            counts['missingInventoryQueries'] += row['inventoryBefore'] is None
            counts['candidateSetUnavailableQueries'] += row['candidateSetStatus'] == 'NOT_GENERATED_OR_UNAVAILABLE'
            counts['generatedEmptyCandidateSetQueries'] += row['candidateSetStatus'] == 'EXPORTED_EMPTY_CANDIDATE_SET'
            exclusions.update(row['bundleExclusionReasons'])
            future_errors.update(c['evidence'] for c in row['subsequentCompletedItems'] if c['evidence'] != 'RECIPE_MEMBERSHIP_ONLY')
            counts['laterRecipeLinkedQueries'] += any(c['relatedOutsideItemIds'] for c in row['subsequentCompletedItems'])
            for item in row['outsideTargetPathItems']:
                assert item in row['observedPurchaseBundle']
                assert int(item) not in row['targetRecipePath']
                assert all(item not in candidate for candidate in row['candidateSet'])
                # Membership does not depend on quantity; one row contributes once per distinct outside item.
                key = (row['championId'] or 'UNKNOWN', row['role'] or 'UNKNOWN', row['targetItemId'], int(item))
                add_purchase(patterns[key], row, int(item))
            if len(examples) < 5 and not row['bundleExclusionReasons']:
                examples.append(row)
    assert counts['outsideQueries'] == export['outsideTargetPathPurchases']
    assert counts['missingChampionQueries'] == export['outsideMissingChampionCount']
    rows = sorted((pattern_row(key, bucket, name) for key, bucket in patterns.items()),
                  key=lambda r: (-r['queryCount'], str(r['championId']), r['role'], r['targetItemId'], r['outsideItemId']))
    fields = list(pattern_row(('UNKNOWN', 'UNKNOWN', 0, 0),
                              {**accumulator(), 'queries': 1}, name))
    write_csv(args.out_dir / 'preparation-patterns.csv', rows, fields)
    write_json(args.out_dir / 'examples.json', examples)
    summary = {'export': export, 'counts': dict(counts), 'bundleExclusionReasonsOverlapping': dict(exclusions),
               'futureCompletionUnresolvedCounts': dict(future_errors), 'patternCount': len(rows),
               'evidence': 'RECIPE_MEMBERSHIP_ONLY; no actual material consumption or causal value inferred'}
    write_json(args.out_dir / 'summary.json', summary)
    sources = [source, args.input_dir / 'candidate-scope-unresolved.jsonl.gz',
               args.input_dir / 'candidate-scope-summary.json', args.catalog, Path(__file__)]
    for field in ('participantMetadataSource', 'comparisonSource'):
        if export.get(field):
            sources.append(Path(export[field]))
    project = Path(__file__).resolve().parents[1]
    sources += [project / path for path in (
        'src/main/java/dfgg/domain/match/TargetRecipePath.java',
        'src/main/java/dfgg/domain/match/PurchaseRecipeMatcher.java',
        'src/main/java/dfgg/application/match/ComponentCandidateScopeDiagnostics.java',
        'src/main/java/dfgg/application/match/ComponentTrainingDataService.java',
        'src/test/java/dfgg/application/match/ComponentDatasetExportTest.java')]
    write_json(args.out_dir / 'analysis-plan.json', {
        'sources': [{'path': str(path.resolve()), 'sha256': sha256(path)} for path in sources],
        'patternKey': ['championId', 'role', 'targetItemId', 'outsideItemId'],
        'countUnit': 'query per distinct outside item; quantities retained in source JSONL',
        'cleanBundle': 'bundleExclusionReasons is empty; not a claim of complete state or causal validity',
        'futureLink': 'same participant, later purchase order, confirmed non-undone CORE/tier-two boots purchase; '
                      'outside item belongs to its recipe and is not the completed item itself',
        'time': 'first related completion minus bundle first purchase time; null for invalid/missing time',
        'promotion': 'NONE; all rows are diagnostics only'})
    report(args, export, summary, rows, name)
    print(json.dumps(summary, ensure_ascii=False))


def report(args, export, summary, rows, name):
    count = summary['counts']
    pct = lambda n, total: f'{n / total:.1%}' if total else 'N/A'
    comparison = (f"기존 학습 export와 {export['comparedTrainingRows']:,}개 행을 JSON 의미 기준으로 비교했다. 후보 순서·수량·Label·Feature·학습 상태·메타데이터가 모두 같았다."
                  if export['comparisonSource'] and export['comparedTrainingRows'] == export['bundles']
                  else '기존 학습 export 전체와의 동등성 비교는 수행하지 않았다.')
    lines = ['# Component Candidate 범위 진단', '',
             f"{export['matches']:,}경기 / {export['bundles']:,}개 구매 묶음을 진단했다. {comparison}", '',
             f"경로 밖 구매는 {count['outsideQueries']:,}개 묶음이다. 이 중 기존 bundle 제외 사유가 없는 것은 {count['cleanBundleQueries']:,}개다. 경로 밖 구매가 자동으로 Preparation Candidate 또는 가치 있는 선구매를 뜻하지는 않는다.", '',
             '## 판정과 분모', '',
             'TargetRecipePath는 기존 PurchaseRecipeMatcher.remainingPath(target, empty)로 전체 경로를 얻고, 동등 변형 재료는 기존 materialsFor 판정을 재사용한다. 목표 자체도 경로 안이다. 예산·슬롯 부족·보유 재료 때문에 구매 후보가 없거나 재료 수량이 초과돼도 그것만으로 경로 밖으로 분류하지 않는다.', '',
             '기존 예제가 선택한 사후 관측 Target을 진단에 사용한다. 관측 구매 묶음에 경로 안/밖 구매가 섞이면 outsideTargetPathItems에 경로 밖 항목과 수량만 남긴다. 카탈로그 누락·순환은 미확정 상태로 기록한다. 취소/UNDO·시각 오류·목표 경계를 넘은 묶음은 별도 상태로 집계한다.', '',
             '| 상태 | 묶음 수 |', '|---|---:|']
    lines += [f'| {status} | {n:,} |' for status, n in export['statuses'].items()]
    lines += ['', '경로 밖 데이터의 기존 학습 상태:', '', '| 학습 상태 | 묶음 수 |', '|---|---:|']
    lines += [f'| {status} | {n:,} |' for status, n in export['outsideTrainingStatuses'].items()]
    lines += ['', '## 이후 빌드 연결', '',
              f"경로 밖 묶음 중 {count['laterRecipeLinkedQueries']:,}개({pct(count['laterRecipeLinkedQueries'], count['outsideQueries'])})에서 이후 구매한 완성품의 레시피에 해당 아이템이 포함됐다.", '',
              '이는 RECIPE_MEMBERSHIP_ONLY다. 판매·재구매·다른 재료의 소비를 추적한 증명이 아니며, 해당 구매가 실제로 그 완성품에 소비됐다고 단정하지 않는다. 이후 완성품은 같은 참가자의 CORE 및 2티어 신발 구매이며 취소·취소 미확정 구매를 제외한다. 자동 변형으로만 얻은 완성품은 이 목록에 포함하지 않는다. 관측 경기 종료 뒤의 빌드는 알 수 없다.', '',
              '## 반복 패턴', '',
              '아래는 Champion이 확인되고 기존 bundle 제외 사유가 없는 관측이 많은 패턴 15개다. 전체 패턴과 제외 사유가 있는 표본까지의 분모는 preparation-patterns.csv에 함께 기록했다. Champion별 빈도는 참가자 수·챔피언 출현 빈도를 보정한 확률이 아니다.', '',
              '| Champion ID | Role | Target | 경로 밖 구매 | 전체 Query | 제외 사유 없는 Query | 그중 이후 레시피 연결 |',
              '|---|---|---|---|---:|---:|---:|']
    ranked = sorted([r for r in rows if r['championId'] != 'UNKNOWN' and r['cleanBundleQueryCount']],
                    key=lambda r: (-r['cleanBundleQueryCount'], -r['queryCount']))[:15]
    for row in ranked:
        lines.append(f"| {row['championId']} | {row['role']} | {row['targetItemName']} ({row['targetItemId']}) | {row['outsideItemName']} ({row['outsideItemId']}) | {row['queryCount']} | {row['cleanBundleQueryCount']} | {pct(row['cleanLaterRecipeLinkedQueryCount'], row['cleanBundleQueryCount'])} |")
    lines += ['', '## 데이터 제한', '',
              f"경로 밖 묶음 중 Champion 누락 {count['missingChampionQueries']:,}개, Inventory 누락 {count['missingInventoryQueries']:,}개, 후보 집합이 생성/제공되지 않은 묶음 {count['candidateSetUnavailableQueries']:,}개다. 후보를 생성했지만 가능한 후보가 0개인 묶음은 별도로 {count['generatedEmptyCandidateSetQueries']:,}개다. 누락값은 추정으로 채우지 않았다. trainingStatus와 candidateSetStatus를 함께 확인해야 한다.", '',
              '대부분의 비학습 묶음도 진단할 수 있게 기존 BUNDLE_EXCLUDED 여부를 보존한다. 진단 상태를 학습 READY로 바꾸지 않는다. 현재 target 선택 기준·구매 묶음 분할·취소 및 Inventory 복원 정책의 한계는 그대로 적용된다. 반복 Query는 같은 경기/참가자에 속할 수 있어 독립 표본이 아니다.', '',
              '## 결과 파일과 재현', '',
              f'- 원본 경로 밖 구매: `{(args.input_dir / "out-of-target-path-purchases.jsonl.gz").resolve()}`',
              f'- 취소/미확정 진단: `{(args.input_dir / "candidate-scope-unresolved.jsonl.gz").resolve()}`',
              '- preparation-patterns.csv: Champion / Role / Target / 경로 밖 아이템별 반복성과 이후 레시피 연결 집계',
              '- examples.json: 기존 bundle 제외 사유가 없는 실제 사례 5개',
              '- summary.json: 전체 분모, 상태, 누락 및 제외 사유',
              '- analysis-plan.json: 집계 정의와 입력 SHA-256', '',
              '```sh',
              'python3 scripts/component-candidate-scope-analyze.py --input-dir build/component-candidate-scope-16.18 --catalog build/component-data/observed-core-v4-role-slots-16.18/catalog.json --out-dir component_candidate_scope_diagnostics',
              '```', '',
              '이번 결과로 Candidate 승격 규칙을 만들지 않았다. 이후 반복성·세밀한 상태 통제·실제 재료 소비와의 연결·추론 시 재현 가능한 조건을 검증한 뒤 제한적인 Preparation Candidate 정책을 검토할 수 있다.']
    (args.out_dir / 'report.md').write_text('\n'.join(lines) + '\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--input-dir', type=Path, required=True)
    parser.add_argument('--catalog', type=Path, required=True)
    parser.add_argument('--out-dir', type=Path, required=True)
    analyze(parser.parse_args())
