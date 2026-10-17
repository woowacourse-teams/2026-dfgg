"""Denominator and frozen-dataset regression tests; no database required."""
import copy
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('evaluation', Path(__file__).with_name('component-evaluation.py'))
evaluation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(evaluation)


def row(status='READY', count=2, source='TARGET_PATH'):
    candidates = [dict(itemQuantities={'1001': 1}, candidateSource=source, label=1,
                       features=[0.0] * 59, purchaseOrder=[1001], inventoryAfter={'1001': 1}),
                  dict(itemQuantities={'1002': 1}, candidateSource='TARGET_PATH', label=0,
                       features=[0.0] * 59, purchaseOrder=[1002], inventoryAfter={'1002': 1})][:count]
    return dict(matchId='match', participantId=1, startPurchaseOrder=1, endPurchaseOrder=1,
                targetItemId=3001, position='BOTTOM', completedCoreCount=0,
                inventoryBefore={}, goldLower=1000, goldUpper=1200, observedItems={'1001': 1},
                status=status, exclusionReasons=[], candidates=candidates)


class EvaluationTest(unittest.TestCase):
    def test_empty_candidates_stay_in_recall_denominator(self):
        coverage = evaluation.Coverage()
        coverage.observe(row())
        coverage.observe(row('OBSERVED_BUNDLE_MISSING', 0))
        recall = coverage.result()['candidate_recall']['all']
        self.assertEqual(recall['comparable'], 2)
        self.assertEqual(recall['recall'], .5)
        self.assertEqual(recall['cost_confirmed_recall'], .5)

    def test_one_candidate_counts_for_recall_but_not_ranking_funnel(self):
        coverage = evaluation.Coverage()
        coverage.observe(row(count=1))
        result = coverage.result()
        self.assertEqual(result['ready_queries'], 1)
        self.assertEqual(result['funnel'][-1]['count'], 0)
        self.assertEqual(result['candidate_recall']['all']['recall'], 1)

    def test_source_contributions_share_denominator_and_sum_to_total(self):
        coverage = evaluation.Coverage()
        for query in [row(), row(source='PREPARATION'), row('OBSERVED_BUNDLE_MISSING', 0)]:
            coverage.observe(query)
        recall = coverage.result()['candidate_recall']['all']
        self.assertAlmostEqual(recall['recall'], recall['target_path_contribution'] + recall['preparation_contribution'])
        self.assertAlmostEqual(recall['preparation_contribution'], 1 / 3)

    def test_unconfirmed_cost_is_not_in_confirmed_denominator(self):
        coverage = evaluation.Coverage()
        coverage.observe(row('OBSERVED_COST_NOT_CONFIRMED', 0))
        self.assertIsNone(coverage.result()['candidate_recall']['all']['cost_confirmed_recall'])

    def test_funnel_is_nested_and_first_drops_account_for_all_rows(self):
        coverage = evaluation.Coverage()
        missing = row('BUNDLE_EXCLUDED', 0)
        missing.update(targetItemId=None, inventoryBefore=None, exclusionReasons=['INVENTORY_UNKNOWN', 'COST_UNKNOWN'])
        for query in [missing, row(), row('OBSERVED_BUNDLE_MISSING', 0)]:
            coverage.observe(query)
        result = coverage.result()
        counts = [s['count'] for s in result['funnel']]
        self.assertEqual(counts, sorted(counts, reverse=True))
        self.assertEqual(sum(result['first_drop_reasons'].values()) + counts[-1], counts[0])

    def test_core_miss_candidate_miss_and_inference_failure_are_zero_not_excluded(self):
        rows = [dict(status='EVALUATED', observedCoreRank=2, observedComponentRank=1),
                dict(status='EVALUATED', reason='CORE_MISS'),
                dict(status='EVALUATED', observedCoreRank=1, reason='CANDIDATE_MISS'),
                dict(status='INFERENCE_FAILED', reason='SERVING_INFERENCE_FAILED'),
                dict(status='EXCLUDED', reason='REPLAY_EVENTS_MISSING')]
        result = evaluation.connected_metrics(rows)
        self.assertEqual(result['evaluated_queries'], 4)
        self.assertEqual(result['coverage'], .8)
        self.assertEqual(result['core_recall']['recall@5'], .5)
        self.assertEqual(result['end_to_end']['hit@1'], .25)
        self.assertEqual(result['end_to_end']['mrr'], .25)
        self.assertEqual(result['target_branch_candidate_recall_given_core_hit'], .5)

    def test_zero_denominator_is_unavailable_not_perfect_or_zero_accuracy(self):
        self.assertIsNone(evaluation.ranking([])['hit@1'])
        self.assertIsNone(evaluation.connected_metrics([])['core_recall']['recall@5'])

    def test_lookup_ranking_metadata_may_change_but_candidate_patterns_must_not(self):
        before = dict(patch='16.18', trainMatchIds=['train'], minPatternCount=50,
                      minFutureRecipeLinkRate=.7, maxCandidatesPerKey=3,
                      entries=[dict(championId=81, role='BOTTOM', targetItemId=3078,
                                    items=[dict(itemId=3070, count=100, futureRecipeLinkRate=.8)])])
        after = copy.deepcopy(before)
        after['entries'][0]['items'][0]['observationRate'] = .2
        evaluation.validate_lookup_candidates(before, after)
        after['entries'][0]['items'][0]['itemId'] = 1001
        with self.assertRaises(ValueError):
            evaluation.validate_lookup_candidates(before, after)

    def test_dataset_preservation_rejects_candidate_and_feature_drift(self):
        before = row()
        after = copy.deepcopy(before)
        evaluation.assert_preserved(before, after)
        after['candidates'][0]['features'][0] = 1
        with self.assertRaises(ValueError):
            evaluation.assert_preserved(before, after)
        after = copy.deepcopy(before)
        after['candidates'][0]['candidateSource'] = 'PREPARATION'
        with self.assertRaises(ValueError):
            evaluation.assert_preserved(before, after)


if __name__ == '__main__':
    unittest.main()
