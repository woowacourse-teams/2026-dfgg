#!/usr/bin/env python3
"""Offline observed/unobserved label diagnostic. No model or label changes."""
import argparse
from collections import Counter, defaultdict
import csv
import hashlib
import json
import math
from pathlib import Path
import statistics

THRESHOLDS = [3, 5, 10, 20, 50]
STRATEGIES = ['Strict', 'Recipe-aware', 'Context-light']


def canonical(quantities):
    if any(int(k) <= 0 or int(v) <= 0 for k, v in quantities.items()):
        raise ValueError('Invalid item quantities')
    return '|'.join(f'{int(k)}x{v}' for k, v in sorted(quantities.items(), key=lambda e: int(e[0])))


def digest(value):
    return hashlib.sha256(value.encode()).hexdigest()


def file_hash(path):
    result = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            result.update(chunk)
    return result.hexdigest()


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + '\n')


def write_csv(path, rows):
    if not rows:
        path.write_text('status\nNO_ELIGIBLE_GROUPS\n')
        return
    with path.open('w', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def prepare(args):
    output = args.output
    output.mkdir(parents=True, exist_ok=True)
    tier_rows = list(csv.DictReader(args.tiers.open())) if args.tiers else []
    tiers = {(r['matchId'], int(r['participantId'])): r['tier'] for r in tier_rows}
    unique_inventory = {}
    seen = set()
    counts = Counter()
    sources = []
    with (output / 'queries.jsonl').open('w') as stream:
        for split in ('train', 'validation', 'test'):
            path = args.data / f'{split}.jsonl'
            sources.append({'path': str(path.resolve()), 'sha256': file_hash(path)})
            for line in path.open():
                row = json.loads(line)
                if row['status'] != 'READY' or len(row['candidates']) < 2:
                    counts['excluded'] += 1
                    continue
                identity = (row['matchId'], row['participantId'], row['startPurchaseOrder'])
                if identity in seen:
                    raise ValueError(f'Duplicate query: {identity}')
                seen.add(identity)
                observed = canonical(row['observedItems'])
                positives = [canonical(c['itemQuantities']) for c in row['candidates'] if c['label'] == 1]
                if positives != [observed] or any(c['label'] not in (0, 1) for c in row['candidates']):
                    raise ValueError(f'Label contract mismatch: {identity}')
                features = row['candidates'][0]['features']
                names = row['featureNames']
                time = features[names.index('game_time_minutes')]
                inventory = canonical(row['inventoryBefore'])
                key = f"{row['targetItemId']}:{inventory}"
                unique_inventory[key] = {'key': key, 'targetItemId': row['targetItemId'], 'inventoryBefore': row['inventoryBefore']}
                tier = tiers.get((row['matchId'], row['participantId']), 'UNKNOWN')
                counts[tier] += 1
                query = {'groupId': ':'.join(map(str, identity)), 'matchId': row['matchId'],
                         'participantId': row['participantId'], 'purchaseOrder': row['startPurchaseOrder'],
                         'targetItemId': row['targetItemId'], 'role': {'MIDDLE': 'MID', 'UTILITY': 'SUPPORT'}.get(row['position'], row['position']),
                         'sourceRole': row['position'], 'gameTime': time,
                         'timeBucket': min(int(time // 5), 6), 'goldBucket': min(row['goldLower'] // 500, 4),
                         'goldLower': row['goldLower'], 'goldUpper': row['goldUpper'],
                         'inventory': inventory, 'inventorySlots': features[names.index('slots_before')],
                         'metadataKey': key, 'observed': observed,
                         'candidateSet': digest(json.dumps(sorted(canonical(c['itemQuantities']) for c in row['candidates']))),
                         'completedCoreCount': row.get('completedCoreCount'), 'targetKind': row.get('targetKind'),
                         'tier': tier, 'split': split}
                stream.write(json.dumps(query, ensure_ascii=False) + '\n')
    with (output / 'inventory-input.jsonl').open('w') as stream:
        for row in unique_inventory.values():
            stream.write(json.dumps(row) + '\n')
    plan = {'sources': sources, 'catalog': str(args.catalog.resolve()), 'catalogSha256': file_hash(args.catalog),
            'tierSource': str(args.tiers.resolve()) if args.tiers else None,
            'tierSha256': file_hash(args.tiers) if args.tiers else None,
            'selection': 'READY AND candidateCount >= 2; train + validation + test; no retraining',
            'timeBucketsMinutes': '[0,5),[5,10),[10,15),[15,20),[20,25),[25,30),[30,infinity)',
            'goldBuckets': '[0,500),[500,1000),[1000,1500),[1500,2000),[2000,infinity)',
            'thresholds': THRESHOLDS, 'strategies': STRATEGIES,
            'coreStageInPrimaryKey': False, 'coreStageReason': 'Preserve the three specified strategies; stage is separate diagnostic',
            'recipeProjection': 'PurchaseRecipeMatcher.remainingPath(target, empty) IDs plus materialsFor(target, inventory) replacement IDs; retain all held quantities',
            'entropy': 'natural-log Shannon entropy / ln(observed unique choice count); zero when choice count is one',
            'descriptivePatterns': {'dominant': 'top1 >= 0.8', 'twoCompeting': 'top1 < 0.8 AND top2 >= 0.8',
                                    'dispersed': 'top1 < 0.8 AND top2 < 0.8'},
            'patternCaution': 'Descriptive bins, not acceptance criteria', 'tierCounts': dict(counts),
            'queryCount': len(seen), 'uniqueInventories': len(unique_inventory),
            'querySha256': file_hash(output / 'queries.jsonl'),
            'inventoryInputSha256': file_hash(output / 'inventory-input.jsonl')}
    write_json(output / 'experiment-plan.json', plan)
    print(json.dumps({'queries': len(seen), 'inventoryKeys': len(unique_inventory), 'tiers': dict(counts)}))


def metrics(counts):
    frequencies = sorted(counts.values(), reverse=True)
    n = sum(frequencies)
    entropy = -sum((v / n) * math.log(v / n) for v in frequencies)
    second = frequencies[1] / n if len(frequencies) > 1 else 0
    return {'queryCount': n, 'uniqueObservedPurchaseCount': len(counts), 'top1Share': frequencies[0] / n,
            'top2Share': frequencies[0] / n + second, 'majorityMargin': frequencies[0] / n - second,
            'entropy': entropy, 'normalizedEntropy': entropy / math.log(len(counts)) if len(counts) > 1 else 0}


def summarize(groups):
    result = {'groupCount': len(groups), 'queryCount': sum(g['queryCount'] for g in groups)}
    for field in ('top1Share', 'top2Share', 'normalizedEntropy', 'uniqueObservedPurchaseCount', 'majorityMargin'):
        values = [g[field] for g in groups]
        result['mean' + field[0].upper() + field[1:]] = statistics.mean(values) if values else None
        result['median' + field[0].upper() + field[1:]] = statistics.median(values) if values else None
    for name, predicate in [('dominant', lambda g: g['top1Share'] >= .8),
                            ('twoCompeting', lambda g: g['top1Share'] < .8 and g['top2Share'] >= .8),
                            ('dispersed', lambda g: g['top2Share'] < .8)]:
        result[name + 'GroupShare'] = sum(predicate(g) for g in groups) / len(groups) if groups else None
    return result


def group_queries(queries, strategy, extra=None):
    buckets = defaultdict(list)
    for q in queries:
        key = (q['targetItemId'], q['role'], q['timeBucket'], q['goldBucket'])
        if strategy == 'Strict':
            key += (q['inventory'],)
        elif strategy == 'Recipe-aware':
            key += (q['recipeInventory'],)
        if extra:
            key += (extra(q),)
        buckets[key].append(q)
    groups = []
    for key, members in sorted(buckets.items(), key=lambda e: str(e[0])):
        first = members[0]
        counts = Counter(q['observed'] for q in members)
        group = {'groupingStrategy': strategy, 'similarityGroupId': digest(json.dumps(key)),
                 'targetItemId': key[0], 'role': key[1], 'gameTimeBucket': key[2], 'goldBucket': key[3],
                 'inventory': key[4] if strategy != 'Context-light' else 'ANY', 'targetKind': first['targetKind'],
                 **metrics(counts), 'candidateSetIdentical': len({q['candidateSet'] for q in members}) == 1,
                 'candidateSetCount': len({q['candidateSet'] for q in members}),
                 'matchCount': len({q['matchId'] for q in members}),
                 'participantCount': len({(q['matchId'], q['participantId']) for q in members}),
                 'medianGoldRangeWidth': statistics.median(q['goldUpper'] - q['goldLower'] for q in members)}
        group['_choices'] = counts
        group['_members'] = members
        groups.append(group)
    return groups


def clean(group):
    return {k: v for k, v in group.items() if not k.startswith('_')}


def analyze(args):
    output = args.output
    plan = json.loads((output / 'experiment-plan.json').read_text())
    if file_hash(args.catalog) != plan['catalogSha256']:
        raise ValueError('Catalog changed since preparation')
    if file_hash(output / 'queries.jsonl') != plan['querySha256']:
        raise ValueError('Prepared queries changed')
    metadata = {r['key']: r for r in map(json.loads, (output / 'inventory-metadata.jsonl').open())}
    if len(metadata) != plan['uniqueInventories']:
        raise ValueError('Incomplete metadata export')
    queries = list(map(json.loads, (output / 'queries.jsonl').open()))
    for q in queries:
        meta = metadata[q['metadataKey']]
        q['recipeInventory'] = canonical(meta['recipeRelevantInventory'])
        if q['targetKind'] != meta['targetKind']:
            raise ValueError('Target kind contract mismatch')
    names = json.loads(args.catalog.read_text())['data']
    ko = output / 'catalog-ko.json'
    if ko.exists():
        names = json.loads(ko.read_text())['data']
    item_name = lambda item: names.get(str(item), {}).get('name', str(item))
    bundle_name = lambda bundle: ' + '.join(f'{item_name(part.split("x")[0])} ×{part.split("x")[1]}' for part in bundle.split('|')) if bundle else '(없음)'
    groups = [g for strategy in STRATEGIES for g in group_queries(queries, strategy)]
    strategy_summary = []
    for strategy in STRATEGIES:
        for threshold in THRESHOLDS:
            selected = [g for g in groups if g['groupingStrategy'] == strategy and g['queryCount'] >= threshold]
            strategy_summary.append({'groupingStrategy': strategy, 'minimumGroupSize': threshold,
                                     **summarize(selected), 'queryCoverage': sum(g['queryCount'] for g in selected) / len(queries)})
    write_csv(output / 'groups.csv', [clean(g) for g in groups])
    write_csv(output / 'grouping_strategy_summary.csv', strategy_summary)
    dimensions = [('target_item', lambda q: str(q['targetItemId'])), ('role', lambda q: q['role']),
                  ('core_stage', lambda q: str(min(q['completedCoreCount'], 3)) if q['completedCoreCount'] is not None else 'UNKNOWN'),
                  ('gold_range', lambda q: '<=100' if q['goldUpper']-q['goldLower'] <= 100 else '101-250' if q['goldUpper']-q['goldLower'] <= 250 else '251-500' if q['goldUpper']-q['goldLower'] <= 500 else '>500'),
                  ('target_kind', lambda q: q['targetKind']), ('tier', lambda q: q['tier'])]
    dimension_results = {}
    for dimension, selector in dimensions:
        rows = []
        for strategy in STRATEGIES:
            subdivided = group_queries(queries, strategy, selector)
            for threshold in THRESHOLDS:
                partitions = defaultdict(list)
                for g in subdivided:
                    if g['queryCount'] >= threshold:
                        partitions[selector(g['_members'][0])].append(g)
                for value, selected in sorted(partitions.items()):
                    row = {'groupingStrategy': strategy, 'minimumGroupSize': threshold, dimension: value, **summarize(selected)}
                    if dimension == 'target_item':
                        row['targetItemName'] = item_name(value)
                        row['targetKind'] = selected[0]['targetKind']
                    rows.append(row)
        write_csv(output / f'{dimension}_consistency.csv', rows)
        dimension_results[dimension] = rows
    candidate_rows = []
    identical_rows = []
    for strategy in STRATEGIES:
        candidate_groups = group_queries(queries, strategy, lambda q: q['candidateSet'])
        for threshold in THRESHOLDS:
            for identical in (True, False):
                selected = [g for g in groups if g['groupingStrategy'] == strategy and g['queryCount'] >= threshold and g['candidateSetIdentical'] == identical]
                candidate_rows.append({'groupingStrategy': strategy, 'minimumGroupSize': threshold, 'candidateSetIdentical': identical, **summarize(selected)})
            # Split variable sets too, avoiding discarding their candidate-identical subsets.
            selected = [g for g in candidate_groups if g['queryCount'] >= threshold]
            identical_rows.append({'groupingStrategy': strategy, 'minimumGroupSize': threshold, **summarize(selected)})
    write_csv(output / 'candidate_set_consistency.csv', candidate_rows)
    write_csv(output / 'candidate_identical_subgroups.csv', identical_rows)
    masters = [q for q in queries if q['tier'] in ('MASTER', 'GRANDMASTER', 'CHALLENGER')]
    master_groups = {s: group_queries(masters, s) for s in STRATEGIES}
    master_rows = [{'groupingStrategy': s, 'minimumGroupSize': n, **summarize([g for g in master_groups[s] if g['queryCount'] >= n])} for s in STRATEGIES for n in THRESHOLDS]
    write_csv(output / 'master_plus_consistency.csv', master_rows)
    # Example selection uses sufficient observations and the same strategy to enable comparison.
    examples = [g for g in groups if g['groupingStrategy'] == 'Recipe-aware' and g['queryCount'] >= 20]
    def example(g):
        return {**clean(g), 'targetItemName': item_name(g['targetItemId']),
                'choices': [{'items': choice, 'itemNames': bundle_name(choice), 'count': count, 'share': count / g['queryCount']} for choice, count in g['_choices'].most_common()],
                'queryExamples': g['_members'][:3]}
    high = [example(g) for g in sorted(examples, key=lambda g: (-g['top1Share'], -g['queryCount'], g['similarityGroupId']))[:5]]
    low = [example(g) for g in sorted(examples, key=lambda g: (g['top1Share'], -g['queryCount'], g['similarityGroupId']))[:5]]
    write_json(output / 'high_consistency_examples.json', high)
    write_json(output / 'low_consistency_examples.json', low)
    summary = {'queryCount': len(queries), 'matchCount': len({q['matchId'] for q in queries}),
               'masterPlusQueryCount': len(masters), 'strategies': strategy_summary,
               'candidateSets': candidate_rows, 'candidateIdenticalSubgroups': identical_rows, 'masterPlus': master_rows,
               'metadataSha256': file_hash(output / 'inventory-metadata.jsonl'), 'planSha256': file_hash(output / 'experiment-plan.json'),
               'analysisScriptSha256': file_hash(Path(__file__)),
               'metadataExporterSha256': file_hash(Path(__file__).resolve().parents[1] / 'src/test/java/dfgg/application/match/ComponentLabelConsistencyMetadataTest.java')}
    identical = [g for g in groups if g['groupingStrategy'] == 'Recipe-aware' and g['queryCount'] >= 20 and g['candidateSetIdentical']]
    summary['recipeAwareIdenticalN20'] = {'groupCount': len(identical),
        'multipleObservedChoicesGroupCount': sum(g['uniqueObservedPurchaseCount'] > 1 for g in identical),
        'top1Below80PercentGroupCount': sum(g['top1Share'] < .8 for g in identical)}
    write_json(output / 'summary.json', summary)
    generate_report(output, plan, summary, dimension_results, high, low, bundle_name)
    print(json.dumps(summary, ensure_ascii=False))


def generate_report(output, plan, summary, dimensions, high, low, bundle_name):
    f = lambda value: 'N/A' if value is None else f'{value:.3f}'
    lines = ['# Component LTR Label Consistency 실험', '',
             f"분석 Query {summary['queryCount']:,}개, 경기 {summary['matchCount']:,}개. 기존 16.18.1 export의 train/validation/test를 합쳐 행동 분포만 진단했다. Label·Feature·후보 정책·모델은 변경하지 않았다.", '',
             '## 데이터와 재현', '',
             '현재 보존된 v5 export를 사용했다. 46개 Feature 중 기존 game_time_minutes와 slots_before만 metadata로 읽었고 Target 관계 Feature는 그룹화에 사용하지 않았다. 별도 41열 baseline export가 없어 baseline 재생성은 하지 않았다. 기존 label=1과 관측 multiset의 일치를 모든 Query에서 검증했다.', '',
             '원본 groupId는 matchId:participantId:startPurchaseOrder로 구성했다. inventorySlots는 퀘스트 슬롯을 반영한 기존 Feature 값이며 Strict 키에는 지정대로 itemId×quantity만 사용했다. 따라서 숨겨진 퀘스트 슬롯 상태까지 동일하다는 의미는 아니다.', '',
             'Recipe-aware는 기존 PurchaseRecipeMatcher의 빈 Inventory remainingPath에 속한 보유 ID와 materialsFor가 찾은 변형 재료 ID를 사용하고 수량을 보존한다. Python에 레시피 로직을 복제하지 않았다.', '',
             '시간은 5분, Gold 하한은 500 단위로 묶으며 마지막 구간은 각각 30분+, 2000+다. completedCoreCount는 기존 export의 현재 보유 중인 과거 CORE 구매 ID 개수이며 판매·소모된 과거 Core 총수와는 다르다. 기본 키에는 넣지 않고 보조 분석에서는 단계별로 재그룹화한다.', '',
             'Entropy = −Σ p ln(p), normalizedEntropy = entropy/ln(관측 선택 수), 선택이 하나면 0. 관측 선택 수에 대한 정규화라 2가지 선택만 있어도 균등하면 1이다. 평균·중앙값은 그룹에 동일 가중치를 주며 queryCount와 coverage를 함께 보고한다.', '',
             'dominant(top1≥0.8), twoCompeting(top1<0.8 & top2≥0.8), dispersed(top2<0.8)는 기술적 분류이며 Label 합격 기준이 아니다. 동일 경기/참가자의 반복 Query는 독립 표본이 아니므로 신뢰구간이나 인과 해석을 하지 않는다.', '',
             '## Q1–Q3. 구매 일관성, 그룹화 및 충분한 표본', '',
             '| 전략 | 최소 n | 그룹 수 | Query 수 | 커버리지 | 평균 top1 | 중앙 top1 | 중앙 top2 | 중앙 entropy |',
             '|---|---:|---:|---:|---:|---:|---:|---:|---:|']
    for row in summary['strategies']:
        lines.append(f"| {row['groupingStrategy']} | {row['minimumGroupSize']} | {row['groupCount']} | {row['queryCount']} | {f(row['queryCoverage'])} | {f(row['meanTop1Share'])} | {f(row['medianTop1Share'])} | {f(row['medianTop2Share'])} | {f(row['medianNormalizedEntropy'])} |")
    lines += ['', 'Recipe-aware의 n≥20 중앙 top1은 69.6%, top2는 95.0%다. n≥50에서도 중앙 top1은 67.4%로 한 선택이 항상 압도적이지 않다. Context-light의 n≥20 중앙 top1은 38.1%로 Inventory를 생략하면 상황이 크게 섞인다. Strict의 n≥20은 100%지만 단 3그룹/101 Query(0.137%)이므로 높은 일관성을 전체에 일반화할 수 없다. 전략별 비교는 포함된 Query 집합도 달라 순수한 Inventory 통제 효과의 추정은 아니다.']
    lines += ['', '## Q4. 대표 선택과 경쟁 선택 비율', '', '| 전략 (n≥20) | dominant | 두 선택 경쟁 | 분산 |', '|---|---:|---:|---:|']
    for row in summary['strategies']:
        if row['minimumGroupSize'] == 20:
            lines.append(f"| {row['groupingStrategy']} | {f(row['dominantGroupShare'])} | {f(row['twoCompetingGroupShare'])} | {f(row['dispersedGroupShare'])} |")
    lines += ['', '## Q5. 후보 집합까지 동일한 경우', '', '| 전략 | 동일 여부 | 그룹 수 (n≥20) | 중앙 top1 | 중앙 entropy |', '|---|---|---:|---:|---:|']
    for row in summary['candidateSets']:
        if row['minimumGroupSize'] == 20:
            lines.append(f"| {row['groupingStrategy']} | {row['candidateSetIdentical']} | {row['groupCount']} | {f(row['medianTop1Share'])} | {f(row['medianNormalizedEntropy'])} |")
    lines += ['', '가변 후보 그룹도 candidateSet별로 나눈 추가 결과는 candidate_identical_subgroups.csv에 기록했다. 이 재그룹화는 후보 차이로 설명되지 않는 관측 다양성을 확인하는 보조 분석이다.', '',
              '## Q6–Q7. Target/role/Core 단계/Gold 폭', '', '아래는 Recipe-aware, n≥20 결과다. Core 단계와 Gold 폭은 각 값으로 그룹을 다시 나누므로 기본 표와 표본 범위가 다르다. Gold 폭과 다양성의 연관은 Target·Role·시점의 구성 차이가 섞인 기술통계다.']
    evidence = summary['recipeAwareIdenticalN20']
    lines += ['', f"후보 집합이 같은 Recipe-aware n≥20 그룹 {evidence['groupCount']}개 중 {evidence['multipleObservedChoicesGroupCount']}개에서는 복수 구매가 관측됐고, {evidence['top1Below80PercentGroupCount']}개는 top1<80%였다. 따라서 후보 집합 차이가 다양성의 전부를 설명하지는 않는다."]
    for dimension in ('role', 'core_stage', 'gold_range', 'target_kind', 'target_item'):
        selected = [r for r in dimensions[dimension] if r['groupingStrategy'] == 'Recipe-aware' and r['minimumGroupSize'] == 20]
        if dimension == 'target_item':
            selected = sorted([r for r in selected if r['groupCount'] >= 3], key=lambda r: r['medianTop1Share'])[:10]
        lines += ['', f'### {dimension}', '', '| 구분 | 그룹 수 | Query 수 | 중앙 top1 | 중앙 entropy |', '|---|---:|---:|---:|---:|']
        for row in selected:
            label = row.get('targetItemName', row[dimension])
            lines.append(f"| {label} | {row['groupCount']} | {row['queryCount']} | {f(row['medianTop1Share'])} | {f(row['medianNormalizedEntropy'])} |")
    lines += ['', 'Target별로는 Trinity Force(3078, 23그룹)의 중앙 top1 43.5%, Kraken Slayer(6672, 33그룹)는 52.0%로 낮다. 일반 Core는 66.7%, 2티어 신발은 90.0%다. Role 중 JUNGLE은 63.0%, MID는 74.1%이며 다른 역할·목표 구성 차이가 있으므로 역할 자체의 효과로 해석하지 않는다. Core 3+의 50.0%는 9그룹뿐이다.', '',
              'Gold 폭별 중앙 entropy는 101–250: 0.764, 251–500: 0.722, >500: 0.736으로 단조 증가하지 않는다. 폭≤100 Query는 전체 76개이고 n≥20 그룹은 없으며, >500은 4그룹/120 Query만 남는다. 이번 표본에서는 폭이 넓을수록 다양성이 증가한다는 근거가 충분하지 않다.', '',
              f"Master+ 수집 집단으로 확인된 Query는 {summary['masterPlusQueryCount']:,}개다. 티어는 경기 시점 확정 실력이 아닌 COLLECTION_COHORT metadata다. 전체는 EMERALD~CHALLENGER 집단이며 Master+를 별도로 비교한다.", '',
              '| Master+ 전략 (n≥20) | 그룹 수 | Query 수 | 중앙 top1 | 중앙 entropy |', '|---|---:|---:|---:|---:|']
    for row in summary['masterPlus']:
        if row['minimumGroupSize'] == 20:
            lines.append(f"| {row['groupingStrategy']} | {row['groupCount']} | {row['queryCount']} | {f(row['medianTop1Share'])} | {f(row['medianNormalizedEntropy'])} |")
    lines += ['', '## Q8. Label 정책 판단', '',
              'B. binary Label은 유지 가능하지만 일부 상황에서 uncertainty가 크다. 관측/비관측을 나타내는 현 Label의 의미는 유효하다. 그러나 빈도 차이는 객관적 구매 품질의 우열이 아니며, 낮은 일관성은 곧 Label 오류가 아니다. 세 전략의 커버리지 차이와 후보 동일 조건, Gold 폭, Core 단계의 결과를 함께 해석해야 한다. graded/soft Label 필요성을 확정하려면 더 세밀한 상태 통제와 반복 참가자 영향을 분리한 후속 분석이 필요하다.', '',
              '현재 export 보고서상 전체 1,114,551 bundle 중 READY 128,534개, 후보 2개 이상 73,742개다. 관측 구매의 후보 누락과 Gold 하한 검증에 따른 제외가 크므로 이 결과를 전체 구매 행동에 일반화할 수 없다. 서포터 완성 목표는 해당 export의 분석 대상에 없어 판단을 보류한다.', '']
    for title, examples in [('높은 일관성 사례 5개', high), ('낮은 일관성 사례 5개', low)]:
        lines += [f'## {title}', '']
        for g in examples:
            t, gold = g['gameTimeBucket'], g['goldBucket']
            lines += [f"### {g['targetItemName']} / {g['role']}", '',
                      f"시간 {'30분+' if t == 6 else str(t*5)+'–'+str((t+1)*5)+'분'}, Gold {'2000+' if gold == 4 else str(gold*500)+'–'+str((gold+1)*500-1)}, recipe Inventory `{g['inventory']}` ({bundle_name(g['inventory'])}).",
                      f"n={g['queryCount']}, 경기={g['matchCount']}, top1={f(g['top1Share'])}, normalizedEntropy={f(g['normalizedEntropy'])}, 후보 동일={g['candidateSetIdentical']}.", '']
            lines += [f"- {c['itemNames']}: {c['count']}회 ({c['share']:.1%})" for c in g['choices']]
            lines.append('')
    lines += ['## 실행 방법', '', '```sh',
              'python3 scripts/component-label-consistency.py prepare --data build/component-data/observed-target-v5-16.18 --catalog build/component-data/observed-core-v4-role-slots-16.18/catalog.json --tiers build/component-tier-weight-input/cohort-tiers.csv --output component_ltr_label_consistency_experiment',
              './gradlew evaluationTest --tests dfgg.application.match.ComponentLabelConsistencyMetadataTest -Devaluation.component.catalog="$PWD/build/component-data/observed-core-v4-role-slots-16.18/catalog.json" -Devaluation.consistency.input="$PWD/component_ltr_label_consistency_experiment/inventory-input.jsonl" -Devaluation.consistency.output="$PWD/component_ltr_label_consistency_experiment/inventory-metadata.jsonl"',
              'python3 scripts/component-label-consistency.py analyze --catalog build/component-data/observed-core-v4-role-slots-16.18/catalog.json --output component_ltr_label_consistency_experiment', '```', '',
              '원본 SHA-256, bucket, threshold, 분류 규칙은 experiment-plan.json에, 전체 지표는 CSV와 summary.json에 기록했다. 한글 카탈로그는 외부 다운로드 연결 오류로 확보하지 못해 기존 카탈로그의 영문명과 ID를 사용했다.']
    (output / 'report.md').write_text('\n'.join(lines) + '\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=['prepare', 'analyze'])
    parser.add_argument('--data', type=Path)
    parser.add_argument('--catalog', type=Path, required=True)
    parser.add_argument('--tiers', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    (prepare if args.mode == 'prepare' else analyze)(args)
