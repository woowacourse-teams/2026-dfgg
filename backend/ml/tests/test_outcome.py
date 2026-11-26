import numpy as np

from dfgg_ltr.dataset import Dataset, QueryMeta
from dfgg_ltr.outcome import bought_rows, log_loss, train_outcome_model


def meta_for(win, purchase_minute=None):
    queries = len(win)
    return QueryMeta(
        qids=[f"q{q}" for q in range(queries)], match_ids=[f"KR_{q}" for q in range(queries)],
        patches=["16.22"] * queries, win=np.asarray(win, dtype=float),
        purchase_minute=np.full(queries, np.nan), team_gold_diff=np.full(queries, np.nan),
        own_total_gold=np.full(queries, np.nan))


def test_bought_rows_keep_the_purchased_row_and_skip_unknown_outcomes():
    dataset = Dataset(
        features=np.array([[1.0], [2.0], [3.0], [4.0], [5.0], [6.0]]), labels=np.array([0, 3, 3, 0, 0, 0]),
        group=np.array([2, 2, 2]), qids=["q0", "q1", "q2"], feature_names=["f"], schema_fingerprint="abc")

    features, wins = bought_rows(dataset, meta_for([1.0, np.nan, 0.0]))

    assert features.tolist() == [[2.0]]
    assert wins.tolist() == [1.0]


def test_outcome_model_learns_win_probability_from_the_purchased_item():
    """산 아이템의 feature가 클수록 이기는 데이터에서, 상수 승률보다 로그 손실이 작아야 한다."""
    rng = np.random.default_rng(3)
    queries, candidates = 3000, 4
    signal = rng.normal(0, 1, queries * candidates)
    labels = np.zeros(queries * candidates, dtype=np.int32)
    chosen = rng.integers(0, candidates, queries)
    labels[np.arange(queries) * candidates + chosen] = 3
    bought_signal = signal[np.arange(queries) * candidates + chosen]
    win = (rng.random(queries) < 1 / (1 + np.exp(-2 * bought_signal))).astype(float)
    dataset = Dataset(
        features=signal.reshape(-1, 1), labels=labels, group=np.full(queries, candidates),
        qids=[f"q{q}" for q in range(queries)], feature_names=["signal"], schema_fingerprint="abc")

    booster = train_outcome_model(dataset, meta_for(win), rounds=100)
    predicted = booster.predict(bought_signal.reshape(-1, 1), num_iteration=booster.best_iteration)

    assert log_loss(predicted, win) < log_loss(np.full(queries, win.mean()), win)
