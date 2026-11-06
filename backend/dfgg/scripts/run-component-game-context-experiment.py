#!/usr/bin/env python3
"""동일 v6 Query/후보/Label에서 기존 46열과 Composition 포함 55열을 비교한다."""
import argparse
import json
import subprocess
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--data-dir', type=Path, required=True)
    parser.add_argument('--out-dir', type=Path, required=True)
    parser.add_argument('--rounds', type=int, default=1200)
    args = parser.parse_args()
    output = args.out_dir.resolve()
    if output.exists() and any(output.iterdir()):
        parser.error('기존 실험을 덮어쓰지 않습니다: ' + str(output))
    output.mkdir(parents=True, exist_ok=True)
    ml = Path(__file__).resolve().parents[2] / 'ml'
    plan = {'dataDirectory': str(args.data_dir.resolve()), 'rounds': args.rounds,
            'comparison': ['baseline_46', 'game_context_55'],
            'selection': 'validation early stopping only; same queries, candidates, labels and seed'}
    (output / 'experiment-plan.json').write_text(json.dumps(plan, indent=2))
    reports = {}
    for name, flags in [('baseline_46', ['--without-composition']), ('game_context_55', [])]:
        command = ['uv', 'run', 'python', '-m', 'component_ltr.train',
                   '--data-dir', str(args.data_dir.resolve()), '--out-dir', str(output / name),
                   '--rounds', str(args.rounds), *flags, '--drop-features',
                   'is_preparation_candidate', 'preparation_observation_rate',
                   'preparation_support_log', 'preparation_future_recipe_link_rate']
        print('TRAIN ' + name, flush=True)
        with (output / (name + '.log')).open('w') as log:
            subprocess.run(command, cwd=ml, stdout=log, stderr=subprocess.STDOUT, check=True)
        reports[name] = json.loads((output / name / 'evaluation.json').read_text())
        print(name + ' ' + json.dumps(reports[name]['model']), flush=True)
    assert reports['baseline_46']['groups'] == reports['game_context_55']['groups']
    comparison = {**plan, 'groups': reports['baseline_46']['groups'],
                  'metrics': {name: report['model'] for name, report in reports.items()},
                  'delta': {metric: reports['game_context_55']['model'][metric] - value
                            for metric, value in reports['baseline_46']['model'].items()},
                  'limitations': ['Observed future target conditional experiment; not end-to-end Core evaluation',
                                  'Tag counts are not damage, CC, healing or range',
                                  'Champion metadata snapshot is not historical patch metadata',
                                  'Core stage slices use existing evaluation metadata only']}
    (output / 'comparison.json').write_text(json.dumps(comparison, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
