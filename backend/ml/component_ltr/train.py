"""학습 실행: 데이터 분할 확인 → 모델 학습 → 평가·분석 호출 → 산출물 저장.

실행: python -m component_ltr.train --data-dir /path/to/jsonl --out-dir /path/to/model
순위 지표는 evaluate.py, 오답·포지션 진단은 analyze.py에 둔다.
"""
from __future__ import annotations

import argparse
import json
import csv
import shutil
import hashlib
from pathlib import Path

import lightgbm as lgb
import numpy as np
from component_ltr.dataset import load_splits
from component_ltr.feature_schema import CONTEXT_FEATURE_START, CONTEXT_FEATURE_END, SCHEMA_VERSION, FEATURE_NAMES
from dfgg_ltr.dataset import Dataset
from component_ltr.evaluate import evaluate, evaluate_baselines, stratified_evaluation
from component_ltr.analyze import validation_report
from dfgg_ltr.model_export import to_export_dict
from dfgg_ltr.reference_predict import predict_with_flat_trees
from dfgg_ltr.train import to_lgb_dataset


def main() -> None:
    parser = argparse.ArgumentParser(description="Component 구매 묶음 LambdaMART 학습")
    parser.add_argument("--data-dir", type=Path, required=True)
    parser.add_argument("--out-dir", type=Path, required=True)
    parser.add_argument("--rounds", type=int, default=300)
    parser.add_argument("--without-context", action="store_true",
                        help="비교 실험용: 게임 시간·포지션만 0으로 고정하여 기존 정보와 비교")
    parser.add_argument("--drop-features", nargs="*", default=[], choices=FEATURE_NAMES[41:],
                        help="실험 전용: Target 관계 열을 실제로 제거한다. 서빙 계약과 호환되지 않는다")
    args = parser.parse_args()
    if args.rounds <= 0:
        parser.error("rounds는 양수여야 합니다")
    # train은 가중치 학습, validation은 종료 시점 선택, test는 마지막 평가에만 사용한다.
    train, valid, test = load_splits(args.data_dir, args.out_dir / "dataset-validation.json")
    if args.without_context:
        # 후보·정답·비용 및 능력치 Feature는 유지한다. 모델이 공통 맥락을 읽지 못하게 하는 제거 실험이다.
        # 모든 데이터 분할에 동일하게 적용하며 이 모델은 실제 맥락 값에도 해당 열을 사용하지 않는다.
        for dataset in (train, valid, test):
            dataset.features[:, CONTEXT_FEATURE_START:CONTEXT_FEATURE_END] = 0.0
    disabled = sorted(set(args.drop_features))
    if disabled:
        keep = [i for i, name in enumerate(FEATURE_NAMES) if name not in disabled]
        names = [FEATURE_NAMES[i] for i in keep]
        fingerprint = hashlib.sha256(json.dumps([SCHEMA_VERSION, names]).encode()).hexdigest()
        train, valid, test = [Dataset(d.features[:, keep], d.labels, d.group, d.qids, names, fingerprint)
                              for d in (train, valid, test)]
    params = {"objective": "lambdarank", "metric": "ndcg", "ndcg_eval_at": [1, 3],
              "learning_rate": 0.05, "num_leaves": 15, "min_data_in_leaf": 50,
              "seed": 158, "deterministic": True, "force_col_wise": True,
              "num_threads": 4, "verbosity": -1}
    train_input = to_lgb_dataset(train)
    history = {}
    booster = lgb.train(params, train_input, num_boost_round=args.rounds,
                        valid_sets=[to_lgb_dataset(valid, reference=train_input)],
                        callbacks=[lgb.early_stopping(30), lgb.record_evaluation(history), lgb.log_evaluation(25)])
    # 분석은 별도 모듈에 위임한다. train은 학습과 결과 저장 순서만 조율한다.
    valid_report = validation_report(booster, valid, args.data_dir / "validation.jsonl", history)
    # 기준선: 추가 구매 비용이 높은 묶음 우선. 평가 데이터로 모델이나 규칙을 조정하지 않는다.
    scores = booster.predict(test.features)
    report = {"target_source": "OBSERVED_FUTURE_TARGET_ITEM", "best_iteration": booster.best_iteration,
              "context_features_enabled": not args.without_context, "dropped_features": disabled,
              "groups": {"train": len(train.group), "validation": len(valid.group), "test": len(test.group)},
              "model": evaluate(test, scores), "cost_desc_baseline": evaluate(test, test.features[:, 0]),
              "baselines": evaluate_baselines(test), "params": params}
    report["stratified_test"] = stratified_evaluation(test, scores, args.data_dir / "test.jsonl")
    report["stratified_validation"] = stratified_evaluation(valid, booster.predict(valid.features), args.data_dir / "validation.jsonl")
    exported = to_export_dict(booster, train.feature_names, train.schema_fingerprint)
    # 기존 평탄 트리 변환이 원본 LightGBM 점수와 같은지 작은 표본으로 확인한다. Java 검증은 후속 단계다.
    sample = test.features[:100]
    flat_scores = [predict_with_flat_trees(exported["trees"], row) for row in sample]
    np.testing.assert_allclose(flat_scores, booster.predict(sample), rtol=1e-10, atol=1e-10)
    exported.update({"model_version": "component-ltr-v5", "target_source": "OBSERVED_FUTURE_TARGET_ITEM", "deployable": False})
    args.out_dir.mkdir(parents=True, exist_ok=True)
    booster.save_model(str(args.out_dir / "booster.txt"))
    for name, value in (("model.json", exported), ("evaluation.json", report), ("validation-diagnostics.json", valid_report)):
        (args.out_dir / name).write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")
    # 데이터 계약과 학습 방식을 구분한다. LambdaRank를 순수 List-wise loss로 표기하지 않는다.
    metadata = {"modelVersion": "component-ltr-v5", "schemaVersion": SCHEMA_VERSION,
                "targetSource": "OBSERVED_FUTURE_TARGET_ITEM", "deployable": False,
                "datasetUnit": "QUERY_LIST", "objective": params["objective"],
                "featureCount": len(train.feature_names), "features": train.feature_names,
                "schemaFingerprint": train.schema_fingerprint, "randomSeed": params["seed"],
                "bestIteration": booster.best_iteration,
                "contextFeaturesEnabled": not args.without_context, "droppedFeatures": disabled,
                "modelSha256": hashlib.sha256((args.out_dir / "model.json").read_bytes()).hexdigest(),
                "recipeDepthDefinition": "minimum actual combine operations; direct target purchase costs one operation"}
    (args.out_dir / "metadata.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2))
    shutil.copyfile(args.data_dir / "manifest.json", args.out_dir / "split_manifest.json")
    with (args.out_dir / "feature_importance.csv").open("w", encoding="utf-8", newline="") as output:
        writer = csv.writer(output)
        writer.writerow(["rank", "feature_name", "gain"])
        gains = booster.feature_importance(importance_type="gain")
        for rank, index in enumerate(np.argsort(-gains, kind="stable"), 1):
            writer.writerow([rank, train.feature_names[index], float(gains[index])])
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
