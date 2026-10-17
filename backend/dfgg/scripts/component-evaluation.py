"""Frozen Component evaluation: complete denominator, conditional ranking, serving replay report."""
import argparse
from collections import Counter, defaultdict
from datetime import datetime
from zoneinfo import ZoneInfo
import gzip
import hashlib
import json
import sys
from pathlib import Path

COMPARABLE = {'READY', 'OBSERVED_BUNDLE_MISSING', 'OBSERVED_COST_MISMATCH',
              'OBSERVED_COST_NOT_CONFIRMED', 'OBSERVED_COST_OUTSIDE_GOLD_RANGE'}
CONFIRMED = {'READY', 'OBSERVED_BUNDLE_MISSING', 'OBSERVED_COST_MISMATCH'}
STAGES = ('all_queries', 'target_identified', 'state_restored', 'candidate_generation_succeeded',
          'nonempty_candidates', 'observed_candidate_present', 'at_least_two_candidates', 'ranking_evaluable')


def rows(path):
    with (gzip.open(path, 'rt') if str(path).endswith('.gz') else open(path)) as source:
        for line in source:
            yield json.loads(line)


def digest(path):
    with open(path, 'rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()


def qid(row):
    return tuple(row[k] for k in ('matchId', 'participantId', 'startPurchaseOrder', 'endPurchaseOrder', 'targetItemId'))


def ratio(numerator, denominator):
    return numerator / denominator if denominator else None


def ranking(ranks):
    return {'queries': len(ranks), 'hit@1': ratio(sum(r == 1 for r in ranks), len(ranks)),
            'hit@3': ratio(sum(r is not None and r <= 3 for r in ranks), len(ranks)),
            'mrr': ratio(sum(1 / r for r in ranks if r is not None), len(ranks))}


def slices(row):
    return ('all', 'role/' + row['position'], 'target/' + str(row['targetItemId']),
            'core_stage/' + str(row['completedCoreCount']))


class Coverage:
    def __init__(self):
        self.stages = Counter()
        self.statuses = Counter()
        self.reasons = Counter()
        self.drops = Counter()
        self.recall = defaultdict(Counter)
        self.ready = 0

    def observe(self, row):
        candidates = row['candidates']
        positive = next((c for c in candidates if c['itemQuantities'] == row['observedItems']), None)
        comparable = row['status'] in COMPARABLE
        self.statuses[row['status']] += 1
        self.reasons.update(row['exclusionReasons'])
        self.ready += row['status'] == 'READY'
        flags = (True, row['targetItemId'] is not None,
                 row['inventoryBefore'] is not None and row['goldLower'] is not None and row['goldUpper'] is not None,
                 comparable, bool(candidates), positive is not None, len(candidates) >= 2, row['status'] == 'READY')
        for stage, passed in zip(STAGES, flags):
            if not passed:
                self.drops[stage + ':' + row['status']] += 1
                break
            self.stages[stage] += 1
        if not comparable:
            return
        for key in slices(row):
            count = self.recall[key]
            count['comparable'] += 1
            count['recalled'] += positive is not None
            if positive:
                count[positive['candidateSource']] += 1
            if row['status'] in CONFIRMED:
                count['cost_confirmed'] += 1
                count['cost_confirmed_recalled'] += positive is not None

    def result(self):
        total = previous = self.stages['all_queries']
        funnel = []
        for stage in STAGES:
            count = self.stages[stage]
            funnel.append(dict(stage=stage, count=count, fraction_of_total=ratio(count, total),
                               fraction_of_previous=ratio(count, previous)))
            previous = count
        recall = {}
        for key, c in sorted(self.recall.items()):
            recall[key] = dict(c, recall=ratio(c['recalled'], c['comparable']),
                              target_path_contribution=ratio(c['TARGET_PATH'], c['comparable']),
                              preparation_contribution=ratio(c['PREPARATION'], c['comparable']),
                              cost_confirmed_recall=ratio(c['cost_confirmed_recalled'], c['cost_confirmed']))
        return dict(funnel=funnel, ready_queries=self.ready, statuses=dict(self.statuses),
                    exclusion_reasons_overlapping=dict(self.reasons), first_drop_reasons=dict(self.drops), candidate_recall=recall)


def assert_preserved(old, new):
    for key in (old.keys() | new.keys()) - {'schemaVersion', 'featureNames', 'candidates'}:
        if old.get(key) != new.get(key):
            raise ValueError(f'Dataset mismatch {qid(new)}: {key}')
    if len(old['candidates']) != len(new['candidates']):
        raise ValueError(f'Candidate count mismatch: {qid(new)}')
    for a, b in zip(old['candidates'], new['candidates']):
        for key in ('itemQuantities', 'purchaseOrder', 'inventoryAfter', 'label', 'candidateSource'):
            if a[key] != b[key]:
                raise ValueError(f'Candidate mismatch {qid(new)}: {key}')
        if a['features'][:46] != b['features'][:46]:
            raise ValueError(f'Feature prefix mismatch: {qid(new)}')


def validate_lookup_candidates(original, current):
    for key in ('patch', 'trainMatchIds', 'minPatternCount', 'minFutureRecipeLinkRate', 'maxCandidatesPerKey'):
        if original[key] != current[key]:
            raise ValueError(f'Lookup candidate policy changed: {key}')
    def candidates(lookup):
        return [(e['championId'], e['role'], e['targetItemId'],
                 [(p['itemId'], p['count'], p['futureRecipeLinkRate']) for p in e['items']])
                for e in lookup['entries']]
    if candidates(original) != candidates(current):
        raise ValueError('Lookup candidate patterns changed since full export')


def offline(args):
    import lightgbm as lgb
    import numpy as np
    sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "ml"))
    from dfgg_ltr.model_export import flatten_model
    output = args.output
    output.mkdir(parents=True, exist_ok=True)
    if any(output.iterdir()):
        raise ValueError('Evaluation output must be empty')
    manifest = json.loads((args.data / 'manifest.json').read_text())
    assignments = {m: v['split'] for m, v in manifest['matches'].items()}
    old_manifest = json.loads((args.all_data.parent / 'manifest.json').read_text())
    if assignments != {m: v['split'] for m, v in old_manifest['matches'].items()}:
        raise ValueError('All-row and ranking split assignments differ')
    metadata = json.loads((args.model / 'metadata.json').read_text())
    model_json = json.loads((args.model / 'model.json').read_text())
    if metadata['featureCount'] != 59 or metadata['droppedFeatures']:
        raise ValueError('Full 59-feature frozen model required')
    for name, expected in [('model.json', metadata['modelSha256']), ('preparation-lookup.json', metadata['preparationLookupSha256'])]:
        if digest(args.model / name) != expected:
            raise ValueError(f'Artifact hash mismatch: {name}')
    for name, key in [('preparation-lookup.json', 'preparationLookupSha256'), ('champion-tags.json', 'championMetadataSha256')]:
        if digest(args.data / name) != metadata[key] or manifest[key] != metadata[key]:
            raise ValueError(f'Dataset/model mismatch: {name}')
    model_manifest = json.loads((args.model / 'split_manifest.json').read_text())
    if model_manifest != manifest:
        raise ValueError('Model training manifest differs from evaluation dataset')
    if model_json['feature_names'] != metadata['features']:
        raise ValueError('Feature contract mismatch')
    current_lookup = json.loads((args.data / 'preparation-lookup.json').read_text())
    original_lookup_path = args.all_data.parent / 'preparation-lookup.json'
    if digest(original_lookup_path) != old_manifest['preparationLookupSha256']:
        raise ValueError('Full export lookup hash mismatch')
    validate_lookup_candidates(json.loads(original_lookup_path.read_text()), current_lookup)
    train_ids = set(current_lookup['trainMatchIds'])
    if train_ids != {m for m, split in assignments.items() if split == 'train'}:
        raise ValueError('Lookup train matches mismatch')
    original_manifest = json.loads((args.original.parent / 'manifest.json').read_text())
    if assignments != {m: v['split'] for m, v in original_manifest['matches'].items()}:
        raise ValueError('Original split mismatch')
    original = {qid(r) for r in rows(args.original)}
    test = list(rows(args.data / 'test.jsonl'))
    current = {qid(r): r for r in test}
    if len(current) != len(test) or not original <= current.keys():
        raise ValueError('Duplicate/missing original test queries')
    coverage = Coverage()
    counts = defaultdict(Counter)
    matched = set()
    source_schemas = Counter()
    with (output / 'replay-input.jsonl').open('w') as replay:
        for row in rows(args.all_data):
            split = assignments[row['matchId']]
            counts[split]['all_queries'] += 1
            counts[split]['all_candidates'] += len(row['candidates'])
            if split != 'test':
                continue
            coverage.observe(row)
            source_schemas[row['schemaVersion']] += 1
            if row['status'] in COMPARABLE:
                replay_row = {k: v for k, v in row.items() if k not in ('candidates', 'featureNames')}
                replay.write(json.dumps(replay_row, separators=(',', ':')) + '\n')
            if row['status'] == 'READY' and len(row['candidates']) >= 2:
                key = qid(row)
                if key in matched or key not in current:
                    raise ValueError(f'Duplicate/unexpected all-row ranking query: {key}')
                assert_preserved(row, current[key])
                matched.add(key)
    if matched != current.keys():
        raise ValueError('All-row export does not cover the complete test ranking set')
    booster = lgb.Booster(model_file=str(args.model / 'booster.txt'))
    if flatten_model(booster.dump_model()) != model_json['trees']:
        raise ValueError('Offline booster and Java model trees differ')
    if booster.feature_name() != metadata['features']:
        raise ValueError('Booster feature mismatch')
    scores = booster.predict(np.asarray([c['features'] for r in test for c in r['candidates']]), num_threads=4)
    buckets = defaultdict(list)
    offset = 0
    for row in test:
        if assignments[row['matchId']] != 'test' or row['featureNames'] != metadata['features']:
            raise ValueError('Test feature/split mismatch')
        candidates = row['candidates']
        positives = [i for i, c in enumerate(candidates) if c['label'] == 1]
        if len(positives) != 1 or row['status'] != 'READY' or len(candidates) < 2:
            raise ValueError('Invalid ranking group')
        positive = positives[0]
        if candidates[positive]['itemQuantities'] != row['observedItems']:
            raise ValueError('Observed purchase differs from positive label')
        group = scores[offset:offset + len(candidates)]
        if not np.isfinite(group).all():
            raise ValueError('Nonfinite model score')
        order = np.argsort(-group, kind='stable')
        rank = int(np.flatnonzero(order == positive)[0]) + 1
        source = candidates[positive]['candidateSource']
        origin = 'original' if qid(row) in original else 'newly_usable'
        for key in (*slices(row), source, origin, origin + '/' + source):
            buckets[key].append(rank)
        offset += len(candidates)
    for split in ('train', 'validation', 'test'):
        for row in rows(args.data / (split + '.jsonl')):
            if assignments[row['matchId']] != split:
                raise ValueError('Split leakage')
            counts[split]['ranking_queries'] += 1
            counts[split]['ranking_candidates'] += len(row['candidates'])
        counts[split]['matches'] = sum(v == split for v in assignments.values())
    paths = {'all_data': args.all_data, 'manifest': args.data / 'manifest.json', 'test_data': args.data / 'test.jsonl',
             'original_test': args.original, 'component_model': args.model / 'model.json',
             'booster': args.model / 'booster.txt', 'lookup': args.data / 'preparation-lookup.json',
             'full_export_lookup': original_lookup_path}
    result = dict(dataset=dict(counts), model=metadata, provenance={k: dict(path=str(p.resolve()), sha256=digest(p)) for k, p in paths.items()},
                  all_row_schemas=dict(source_schemas), preserved_test_queries=len(matched), booster_matches_serving_model=True, lookup_candidate_patterns_preserved=True,
                  coverage=coverage.result(), conditional_ranking={k: ranking(v) for k, v in sorted(buckets.items())})
    (output / 'offline-results.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({'coverage': result['coverage']['funnel'], 'ranking': result['conditional_ranking']['all']}, indent=2))


def connected_metrics(replay):
    # Input restoration failures are coverage exclusions. All attempted service calls remain in the denominator.
    attempted = [r for r in replay if r['status'] != 'EXCLUDED']
    ranks = [r.get('observedComponentRank') for r in attempted]
    core_hits = sum(r.get('observedCoreRank') is not None for r in attempted)
    return dict(total_input_queries=len(replay), evaluated_queries=len(attempted), coverage=ratio(len(attempted), len(replay)),
                exclusions=dict(Counter(r['reason'] for r in replay if r['status'] == 'EXCLUDED')),
                failures=dict(Counter(r.get('reason', 'SUCCESS') for r in attempted)),
                core_recall={f'recall@{k}': ratio(sum(r.get('observedCoreRank') is not None and r['observedCoreRank'] <= k for r in attempted), len(attempted)) for k in (1, 3, 5)},
                target_branch_candidate_recall=ratio(sum(r is not None for r in ranks), len(attempted)),
                target_branch_candidate_recall_given_core_hit=ratio(sum(r is not None for r in ranks), core_hits),
                end_to_end=ranking(ranks))


def fmt(value):
    return 'N/A' if value is None else f'{value:.6f}' if isinstance(value, float) else str(value)


def table(headers, entries):
    return '\n'.join(['| ' + ' | '.join(headers) + ' |', '| ' + ' | '.join(['---'] * len(headers)) + ' |',
                      *['| ' + ' | '.join(fmt(v) for v in row) + ' |' for row in entries]])


def mock_markdown(mock):
    parts = ['# Mock Serving Report', '정답률 평가가 아닌 실제 서비스 경로의 smoke/sanity 실행이다. Core 점수는 기존 응답에서 제공하지 않아 기록하지 않는다.']
    for scenario in mock['scenarios']:
        parts += ['## ' + scenario['name'], '```json\n' + json.dumps(scenario['input'], ensure_ascii=False, indent=2) + '\n```',
                  'Sanity: ' + json.dumps(scenario['checks'], ensure_ascii=False)]
        for branch in scenario['branches']:
            parts += [f"### Core {branch['coreRank']}: {branch['targetName']} ({branch['targetItemId']}) — {branch['status']}",
                      table(['Rank', 'Items × quantity', 'Source', 'Score', 'Gold cost'],
                            [(c['rank'], json.dumps(c['items'], ensure_ascii=False), c['source'], c['score'], c['cost']) for c in branch['top3']])]
            if branch.get('reason'):
                parts.append(branch['reason'])
    return '\n\n'.join(parts) + '\n'


def report(args):
    output = args.output
    offline_result = json.loads((output / 'offline-results.json').read_text())
    serving = json.loads((output / 'serving-metadata.json').read_text())
    mock = json.loads((output / 'mock-serving-results.json').read_text())
    if serving['componentModelSha256'] != offline_result['provenance']['component_model']['sha256'] or serving['lookupSha256'] != offline_result['provenance']['lookup']['sha256']:
        raise ValueError('Offline/serving artifact mismatch')
    if serving['championMetadataSha256'] != offline_result['model']['championMetadataSha256']:
        raise ValueError('Offline/serving champion metadata snapshot mismatch')
    manifest = json.loads(Path(offline_result['provenance']['manifest']['path']).read_text())
    if serving['catalogSha256'] != manifest['catalogSha256']:
        raise ValueError('Offline/serving catalog mismatch')
    if serving['replayInputSha256'] != digest(output / 'replay-input.jsonl'):
        raise ValueError('Replay input changed after serving evaluation')
    replay = list(rows(output / 'replay-results.jsonl'))
    expected = {qid(r) for r in rows(output / 'replay-input.jsonl')}
    actual = [qid(r) for r in replay]
    if len(set(actual)) != len(actual) or set(actual) != expected:
        raise ValueError('Missing, duplicate or unexpected replay query identities')
    if len(replay) != offline_result['coverage']['candidate_recall']['all']['comparable']:
        raise ValueError('Replay did not account for every comparable query')
    connected = connected_metrics(replay)
    connected['statistics_contains_match_queries'] = sum(r.get('statisticsContainsMatch', False) for r in replay if r['status'] != 'EXCLUDED')
    connected['fraction_of_all_test_queries'] = ratio(connected['evaluated_queries'], offline_result['coverage']['funnel'][0]['count'])
    result = dict(offline=offline_result, serving=serving, connected=connected, mock=mock)
    (output / 'evaluation-results.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    mocks = mock_markdown(mock)
    (output / 'mock-serving-report.md').write_text(mocks)
    c = offline_result['coverage']
    sections = ['# Component LTR Evaluation Report', f'작성일: {datetime.now(ZoneInfo("Asia/Seoul")).date()} (Asia/Seoul). 모델·후보·라벨·가중치·임계값 및 운영 모델을 변경하지 않았다.',
                '## 1. Dataset / Split', table(['Split', 'Matches', 'All queries', 'All candidates', 'Ranking queries', 'Ranking candidates'],
                [(s, *[v[k] for k in ('matches', 'all_queries', 'all_candidates', 'ranking_queries', 'ranking_candidates')]) for s, v in offline_result['dataset'].items()]),
                f"Component: {offline_result['model']['modelVersion']}, 59 features. Lookup: {offline_result['model']['preparationLookupSchemaVersion']}. 보존 확인: {offline_result['preserved_test_queries']} test ranking queries.",
                '전체 분모 export schema: ' + json.dumps(offline_result['all_row_schemas']) + '. 기존 후보 보존 export이며 59열 전체 재생성 데이터가 아니다.',
                '```json\n' + json.dumps(offline_result['provenance'], indent=2) + '\n```',
                '## 2. Coverage Funnel', table(['Stage', 'Count', 'Total ratio', 'Previous ratio'], [(s['stage'], s['count'], s['fraction_of_total'], s['fraction_of_previous']) for s in c['funnel']]),
                f"READY 전체: {c['ready_queries']}. 분모는 test 구매 묶음(Query) 수이며 단일 구매 이벤트 수가 아니다.",
                '첫 탈락 사유 (상호 배타):\n\n' + table(['Reason', 'Count'], sorted(c['first_drop_reasons'].items())),
                '## 3. Candidate Recall', '비교 가능 분모는 후보 생성 성공 상태이며 빈 후보도 포함한다. source별 값은 동일 분모에 대한 회수 기여율이다. 비용 확인 분모와 source별 정답 수는 JSON에 함께 기록한다.',
                table(['Slice', 'Comparable', 'Recalled', 'Recall', 'TARGET_PATH contribution', 'PREPARATION contribution', 'Cost confirmed n', 'Cost confirmed recalled', 'Cost confirmed recall'], [(k, v['comparable'], v['recalled'], v['recall'], v['target_path_contribution'], v['preparation_contribution'], v.get('cost_confirmed', 0), v.get('cost_confirmed_recalled', 0), v['cost_confirmed_recall']) for k,v in c['candidate_recall'].items()]),
                '## 4. Conditional Component Ranking', '정답 Target이 주어진 READY/후보 2개 이상 Query의 관측 구매 재현 지표다.',
                table(['Slice', 'Queries', 'Hit@1', 'Hit@3', 'MRR'], [(k, v['queries'], v['hit@1'], v['hit@3'], v['mrr']) for k,v in offline_result['conditional_ranking'].items()]),
                '## 5. Core Evaluation', '운영 통계 snapshot 리플레이 진단이다. 독립 holdout 성능으로 해석하지 않는다.',
                '```json\n' + json.dumps(dict(metadata=serving, metrics=connected), ensure_ascii=False, indent=2) + '\n```',
                '## 6. End-to-End Evaluation', table(['Queries', 'Hit@1', 'Hit@3', 'MRR'], [list(connected['end_to_end'].values())]),
                'Core miss·후보 누락·빈 branch·추론 실패는 실행 분모에 남겨 0점으로 계산한다. E2E MRR은 Core Top-5의 정답 Target branch 안의 순위만 사용한다. 복원 불가 Query는 제외 수와 coverage로 별도 표시한다. Core Recall도 구매 Query별 집계이며 고유 Core 완성 이벤트별 지표가 아니다.',
                '## 7. Mock Serving', mocks,
                '## 8. Known Limitations', '- Observed behavior label이며 구매 품질이나 승률의 정답이 아니다.\n- Gold 하한과 Inventory 복원에 의존하며 완전한 prefix-only 정보는 아니다.\n- 기존 조건부 데이터는 실제 미래 Target을 사용한다.\n- Test 재사용에 따른 개발 평가이며 독립 최종 일반화 검증이 아니다.\n- Preparation positive 표본이 적다.\n- Champion tag는 현재 snapshot이며 과거 패치 이력이 아니다.\n- Core 모델·통계 학습 경기와 test의 독립성이 확보되지 않았다.\n- 연결 평가는 기존 정답 Target에 대해 후보 생성이 성공한 비교 가능 Query 중 복원 가능한 subset이다. 이 선택 편향과 제외 범위를 별도 coverage로 공개한다.\n- 실제 서빙 정책상 지원하지 않는 Target branch도 실패 상태로 보존한다.']
    summary = table(['Metric', 'Result', 'Denominator'], [
        ('Ranking coverage', f"{c['funnel'][-1]['fraction_of_total']:.2%}", c['funnel'][0]['count']),
        ('Candidate recall', f"{c['candidate_recall']['all']['recall']:.2%}", c['candidate_recall']['all']['comparable']),
        ('Conditional Hit@1', f"{offline_result['conditional_ranking']['all']['hit@1']:.2%}", offline_result['conditional_ranking']['all']['queries']),
        ('Core Recall@5', fmt(connected['core_recall']['recall@5']), connected['evaluated_queries']),
        ('E2E Hit@1', fmt(connected['end_to_end']['hit@1']), connected['evaluated_queries']),
        ('E2E Hit@3', fmt(connected['end_to_end']['hit@3']), connected['evaluated_queries']),
        ('E2E MRR', fmt(connected['end_to_end']['mrr']), connected['evaluated_queries'])])
    sections.insert(2, summary + '\n\n' +
                    f"연결 평가 {connected['evaluated_queries']}개 중 {connected['statistics_contains_match_queries']}개가 Core 통계에 포함된 경기다. "
                    "Core/E2E 수치는 독립 holdout 정확도가 아니다. 조건부 Ranking과 E2E는 분모가 다르므로 직접 성능 하락분으로 해석하지 않는다.")
    (output / 'evaluation-report.md').write_text('\n\n'.join(sections) + '\n')
    print(json.dumps(connected, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    p = commands.add_parser('offline')
    for name in ('data', 'all-data', 'original', 'model', 'output'):
        p.add_argument('--' + name, type=Path, required=True)
    p = commands.add_parser('report')
    p.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    (offline if args.command == 'offline' else report)(args)
