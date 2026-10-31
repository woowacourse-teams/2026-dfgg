import gzip
import importlib.util
import json
from pathlib import Path

spec = importlib.util.spec_from_file_location('mining', Path(__file__).with_name('component-preparation-mine.py'))
mining = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mining)


def test_train_only_thresholds_and_deterministic_cap(tmp_path):
    manifest = tmp_path / 'manifest.json'
    manifest.write_text(json.dumps({'matches': {'train': {'split': 'train'}, 'heldout': {'split': 'test'}}}))
    def row(match, item, linked=True, excluded=False):
        return dict(matchId=match, dataVersion='16.18.1', championId=81, role='BOTTOM', targetItemId=3078,
                    outsideTargetPathItems={str(item): 1}, bundleExclusionReasons=['INVALID'] if excluded else [],
                    subsequentCompletedItems=[dict(evidence='RECIPE_MEMBERSHIP_ONLY', relatedOutsideItemIds=[item])] if linked else [])
    rows = [row('train', item) for item in (1, 2, 3, 4) for _ in range(50)]
    rows += [row('train', 5, linked=i < 34) for i in range(50)]
    rows += [row('train', 6, excluded=True) for _ in range(500)]
    rows += [row('heldout', 9) for _ in range(1000)]
    source = tmp_path / 'source.gz'
    with gzip.open(source, 'wt') as out:
        for r in rows:
            out.write(json.dumps(r) + '\n')
    result = mining.mine(source, manifest, tmp_path / 'a', '16.18')
    assert [p['itemId'] for p in result['entries'][0]['items']] == [1, 2, 3]
    assert result['trainMatchIds'] == ['train']
    with gzip.open(source, 'wt') as out:
        for r in reversed([r for r in rows if r['matchId'] == 'train']):
            out.write(json.dumps(r) + '\n')
    other = mining.mine(source, manifest, tmp_path / 'b', '16.18')
    assert other['entries'] == result['entries']
