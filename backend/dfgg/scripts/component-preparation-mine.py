"""Freeze a patch lookup using only matches assigned to train before aggregation."""
import argparse
import csv
import gzip
import hashlib
import json
from collections import defaultdict
from pathlib import Path


def sha(path):
    with Path(path).open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()


def mine(source, manifest_path, output, patch):
    manifest = json.loads(manifest_path.read_text())
    train = sorted(m for m, value in manifest['matches'].items() if value['split'] == 'train')
    allowed = set(train)
    stats = defaultdict(lambda: [0, 0, 0])
    output.mkdir(parents=True, exist_ok=True)
    # Materialize the train-only input before computing any pattern statistics.
    train_source = output / 'train-observations.jsonl.gz'
    with gzip.open(source, 'rt') as rows, gzip.open(train_source, 'wt') as frozen:
        for line in rows:
            row = json.loads(line)
            if row['matchId'] not in manifest['matches']:
                raise ValueError('Observation match absent from split manifest')
            if row['matchId'] in allowed:
                frozen.write(line)
    with gzip.open(train_source, 'rt') as rows:
        for line in rows:
            row = json.loads(line)
            assert row['matchId'] in allowed
            if row['dataVersion'].rsplit('.', 1)[0] != patch:
                raise ValueError('Patch mismatch')
            role = (row['role'] or '').strip().upper()
            role = {'MIDDLE': 'MID', 'UTILITY': 'SUPPORT'}.get(role, role)
            if not row['championId'] or role not in {'TOP', 'JUNGLE', 'MID', 'BOTTOM', 'SUPPORT'}:
                raise ValueError('Missing champion or unsupported role')
            linked = {item for completion in row['subsequentCompletedItems']
                      if completion['evidence'] == 'RECIPE_MEMBERSHIP_ONLY'
                      for item in completion['relatedOutsideItemIds']}
            for item in row['outsideTargetPathItems']:
                counts = stats[(row['championId'], role, row['targetItemId'], int(item))]
                counts[0] += 1
                if not row['bundleExclusionReasons']:
                    counts[1] += 1
                    counts[2] += int(int(item) in linked)
    records = []
    qualifying = defaultdict(list)
    for key, (total, eligible, linked) in sorted(stats.items()):
        rate = linked / eligible if eligible else 0.0
        reason = 'MIN_PATTERN_COUNT' if eligible < 50 else 'MIN_FUTURE_RECIPE_LINK_RATE' if rate < .70 else ''
        row = dict(zip(('championId', 'role', 'targetItemId', 'preparationItemId'), key))
        row.update(totalCount=total, eligibleCount=eligible, futureRecipeLinkedCount=linked,
                   futureRecipeLinkRate=rate, promoted=False, rejectionReason=reason)
        records.append(row)
        if not reason:
            qualifying[key[:3]].append(row)
    entries = []
    for (champion, role, target), patterns in sorted(qualifying.items()):
        patterns.sort(key=lambda r: (-r['eligibleCount'], -r['futureRecipeLinkRate'], r['preparationItemId']))
        for row in patterns[:3]:
            row['promoted'] = True
        for row in patterns[3:]:
            row['rejectionReason'] = 'MAX_CANDIDATES_PER_KEY'
        entries.append(dict(championId=champion, role=role, targetItemId=target, items=[
            dict(itemId=r['preparationItemId'], count=r['eligibleCount'], futureRecipeLinkRate=r['futureRecipeLinkRate'])
            for r in patterns[:3]]))
    artifact = dict(schemaVersion='component-preparation-v1', patch=patch,
                    miningSplit='train', trainMatchIds=train, splitManifestSha256=sha(manifest_path),
                    trainObservationsSha256=sha(train_source), minPatternCount=50,
                    minFutureRecipeLinkRate=.70, maxCandidatesPerKey=3, entries=entries)
    (output / 'preparation-lookup.json').write_text(json.dumps(artifact, ensure_ascii=False, indent=2) + '\n')
    if records:
        with (output / 'preparation-patterns.csv').open('w', newline='') as out:
            writer = csv.DictWriter(out, fieldnames=list(records[0]))
            writer.writeheader()
            writer.writerows(records)
    print(json.dumps(dict(trainMatches=len(train), patterns=len(records), promoted=sum(r['promoted'] for r in records), keys=len(entries))))
    return artifact


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--source', type=Path, required=True)
    p.add_argument('--manifest', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--patch', required=True)
    a = p.parse_args()
    mine(a.source, a.manifest, a.output, a.patch)
