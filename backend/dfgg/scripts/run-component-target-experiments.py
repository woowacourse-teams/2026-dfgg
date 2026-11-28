#!/usr/bin/env python3
"""Java export 후 같은 분할에서 full/41열 baseline/5개 제거 실험을 별도 저장한다."""
import argparse
import json
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--data-dir', type=Path, required=True)
parser.add_argument('--out-dir', type=Path, required=True)
parser.add_argument('--rounds', type=int, default=1200)
args = parser.parse_args()
project = Path(__file__).resolve().parents[1]
ml = project.parent / 'ml'
features = ['target_remaining_gold_after_purchase', 'target_remaining_recipe_depth',
            'contains_direct_target_material', 'target_material_coverage_after_purchase', 'target_progress_gain']
experiments = [('full', []), ('baseline_41', features)] + [('without_' + feature, [feature]) for feature in features]
output = args.out_dir.resolve()
output.mkdir(parents=True, exist_ok=True)
plan = {'dataDir': str(args.data_dir.resolve()), 'rounds': args.rounds,
        'experiments': [{'name': name, 'dropFeatures': dropped} for name, dropped in experiments]}
(output / 'experiment-plan.json').write_text(json.dumps(plan, indent=2))
for name, dropped in experiments:
    target = output / name
    if (target / 'model.json').exists():
        raise SystemExit('기존 실험을 덮어쓰지 않습니다: ' + str(target))
    command = ['uv', 'run', 'python', '-m', 'component_ltr.train', '--data-dir', str(args.data_dir.resolve()),
               '--out-dir', str(target), '--rounds', str(args.rounds)]
    if dropped:
        command.extend(['--drop-features', *dropped])
    print('TRAIN ' + name, flush=True)
    with (output / (name + '.log')).open('w') as log:
        subprocess.run(command, cwd=ml, stdout=log, stderr=subprocess.STDOUT, check=True)
    evaluation = json.loads((target / 'evaluation.json').read_text())
    print(name + ' ' + json.dumps(evaluation['model']), flush=True)
