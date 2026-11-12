import importlib.util
import math
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('consistency', Path(__file__).with_name('component-label-consistency.py'))
experiment = importlib.util.module_from_spec(spec)
spec.loader.exec_module(experiment)


class LabelConsistencyTest(unittest.TestCase):
    def test_bundle_ignores_order_and_preserves_quantity(self):
        self.assertEqual(experiment.canonical({'1036': 2, '1028': 1}), '1028x1|1036x2')
        self.assertNotEqual(experiment.canonical({'1036': 2}), experiment.canonical({'1036': 1}))

    def test_single_and_uniform_choices_have_defined_entropy(self):
        single = experiment.metrics({'a': 10})
        self.assertEqual(single['normalizedEntropy'], 0)
        self.assertEqual(single['majorityMargin'], 1)
        uniform = experiment.metrics({'a': 5, 'b': 5})
        self.assertAlmostEqual(uniform['entropy'], math.log(2))
        self.assertAlmostEqual(uniform['normalizedEntropy'], 1)
        self.assertEqual(uniform['top2Share'], 1)
        self.assertEqual(uniform['majorityMargin'], 0)

    def test_metrics_for_known_70_20_10_distribution(self):
        metrics = experiment.metrics({'a': 70, 'b': 20, 'c': 10})
        self.assertAlmostEqual(metrics['top1Share'], .7)
        self.assertAlmostEqual(metrics['top2Share'], .9)
        self.assertAlmostEqual(metrics['majorityMargin'], .5)
        self.assertAlmostEqual(metrics['normalizedEntropy'], .7298466991620975)

    def test_inventory_and_candidate_set_control_change_groups(self):
        base = {'targetItemId': 1, 'role': 'TOP', 'timeBucket': 1, 'goldBucket': 1,
                'inventory': '1036x1', 'recipeInventory': '1036x1', 'targetKind': 'CORE',
                'observed': '1036x1', 'candidateSet': 'A', 'matchId': 'm1', 'participantId': 1,
                'goldLower': 600, 'goldUpper': 800}
        queries = [base, {**base, 'inventory': '1036x1|1054x1', 'observed': '1028x1', 'matchId': 'm2'},
                   {**base, 'recipeInventory': '', 'candidateSet': 'B', 'matchId': 'm3'}]
        strict = experiment.group_queries(queries, 'Strict')
        self.assertEqual(len(strict), 2)
        recipe = experiment.group_queries(queries, 'Recipe-aware')
        self.assertEqual(len(recipe), 2)
        shared = next(g for g in recipe if g['queryCount'] == 2)
        self.assertTrue(shared['candidateSetIdentical'])
        self.assertEqual(shared['top1Share'], .5)
        context = experiment.group_queries(queries, 'Context-light')
        self.assertEqual(len(context), 1)
        self.assertFalse(context[0]['candidateSetIdentical'])
        same_candidates = experiment.group_queries(queries, 'Context-light', lambda q: q['candidateSet'])
        self.assertEqual(len(same_candidates), 2)
        self.assertTrue(all(g['candidateSetIdentical'] for g in same_candidates))


if __name__ == '__main__':
    unittest.main()
