"""실제 Component 모델의 Python/Java 점수 대조용 표본을 만든다."""
import argparse
import hashlib
import json
from pathlib import Path

import lightgbm as lgb
import numpy as np
from component_ltr.dataset import load_dataset
from dfgg_ltr.export_parity_sample import build_parity_sample, threshold_boundary_rows


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--model-dir', type=Path, required=True)
    parser.add_argument('--data', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    model_path = args.model_dir / 'model.json'
    model = json.loads(model_path.read_text())
    dataset = load_dataset(args.data)
    if model['feature_names'] != dataset.feature_names or model['schema_fingerprint'] != dataset.schema_fingerprint:
        raise ValueError('모델과 데이터의 Feature 계약이 다릅니다')
    booster = lgb.Booster(model_file=str(args.model_dir / 'booster.txt'))
    # 첫 행에 치우치지 않도록 고정 시드로 실데이터 1,000개를 뽑는다.
    indices = np.random.default_rng(158).choice(len(dataset.features), min(1000, len(dataset.features)), replace=False)
    rows = dataset.features[indices].tolist()
    boundaries = threshold_boundary_rows(model)
    rows += boundaries + [[None] * len(dataset.feature_names), [0.0] * len(dataset.feature_names)]
    fixture = build_parity_sample(model, rows, len(boundaries))
    native_rows = np.asarray([[np.nan if v is None else v for v in row] for row in rows])
    native_scores = booster.predict(native_rows)
    # 원본 LightGBM과 JSON 평탄 트리가 먼저 같아야 Java 대조도 의미가 있다.
    np.testing.assert_allclose([c['expected_score'] for c in fixture['cases']], native_scores, rtol=1e-10, atol=1e-10)
    for case, score in zip(fixture['cases'], native_scores):
        case['expected_score'] = float(score)
    fixture['model_sha256'] = hashlib.sha256(model_path.read_bytes()).hexdigest()
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(fixture, ensure_ascii=False, indent=2))
    print(f"parity cases={len(rows)}, boundary cases={len(boundaries)}")


if __name__ == '__main__':
    main()
