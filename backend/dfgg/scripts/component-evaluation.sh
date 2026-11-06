#!/usr/bin/env bash
# Frozen evaluation only: no training, model replacement, migration, or statistics aggregation.
set -euo pipefail
if [[ "${1:-}" == "--help" ]]; then
  cat <<'HELP'
Required environment:
  COMPONENT_DATA_DIR       Frozen 59-feature train/validation/test directory
  COMPONENT_ALL_DATA      Full all.jsonl or all.jsonl.gz, including exclusions
  COMPONENT_ORIGINAL_TEST Original target-path test.jsonl (for newly usable slices)
  COMPONENT_MODEL_DIR     Frozen model.json, booster.txt, metadata.json, lookup directory
  CORE_MODEL_PATH         Trained Core model.json
  ITEM_CATALOG_PATH       Matching patch catalog JSON
  EVALUATION_DB_URL       Read-only evaluation DB (participants, events, catalogs, Core statistics)
  EVALUATION_DB_USERNAME / EVALUATION_DB_PASSWORD
  EVALUATION_OUTPUT_DIR   New/empty output directory
Optional: COMPONENT_EVALUATION_PYTHON (defaults to ../ml/.venv/bin/python)
Run from backend/dfgg. Paths may be relative to this directory.
Artifacts: evaluation-report.md, evaluation-results.json, mock-serving-report.md,
mock-serving-results.json, offline-results.json, replay-results.jsonl, serving-metadata.json.
HELP
  exit 0
fi
: "${COMPONENT_DATA_DIR:?}"
: "${COMPONENT_ALL_DATA:?}"
: "${COMPONENT_ORIGINAL_TEST:?}"
: "${COMPONENT_MODEL_DIR:?}"
: "${CORE_MODEL_PATH:?}"
: "${ITEM_CATALOG_PATH:?}"
: "${EVALUATION_DB_URL:?}"
: "${EVALUATION_DB_USERNAME:?}"
: "${EVALUATION_DB_PASSWORD:?}"
: "${EVALUATION_OUTPUT_DIR:?}"
eval_python=${COMPONENT_EVALUATION_PYTHON:-../ml/.venv/bin/python}
absolute_path() { "$eval_python" -c 'from pathlib import Path; import sys; print(Path(sys.argv[1]).resolve())' "$1"; }
component_model_dir=$(absolute_path "$COMPONENT_MODEL_DIR")
core_model_path=$(absolute_path "$CORE_MODEL_PATH")
catalog_path=$(absolute_path "$ITEM_CATALOG_PATH")
output_dir=$(absolute_path "$EVALUATION_OUTPUT_DIR")
"$eval_python" scripts/component-evaluation.py offline \
  --data "$COMPONENT_DATA_DIR" --all-data "$COMPONENT_ALL_DATA" \
  --original "$COMPONENT_ORIGINAL_TEST" --model "$component_model_dir" --output "$output_dir"
./gradlew evaluationTest --tests '*ComponentEvaluationFrameworkTest' \
  "-Drecommendation.ltr.model-path=file:$core_model_path" \
  "-Drecommendation.component-ltr.model-path=file:$component_model_dir/model.json" \
  "-Drecommendation.component-ltr.preparation-lookup-path=file:$component_model_dir/preparation-lookup.json" \
  "-Devaluation.framework.catalog=$catalog_path" \
  "-Devaluation.framework.output=$output_dir"
"$eval_python" scripts/component-evaluation.py report --output "$output_dir"
