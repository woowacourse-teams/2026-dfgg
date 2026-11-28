package dfgg.evaluation;

import dfgg.application.recommend.v3.CandidateSource;
import dfgg.domain.champion.ChampionPosition;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * generator별 Recall@K와 union recall을 누적한다.
 *
 * <p>generator는 최종 순위가 아니라 <b>recall</b>로 평가한다. 각자 다른 이유로 정답을 찾아내
 * union을 두텁게 하는 것이 목적이므로, 개별 NDCG가 낮아도 union에 고유하게 기여하면 값어치가 있다.
 *
 * <p>union recall은 LTR이 넘을 수 없는 천장이다 — 후보에 없는 아이템은 아무리 좋은 랭커도
 * 순위를 매길 수 없다. 이 수치가 낮으면 모델을 학습해봐야 소용이 없다.
 */
public final class RecallTally {

    private final List<Observation> observations = new ArrayList<>();

    private static final int UNSPECIFIED_STEP = -1;

    /** generator 하나만 기록할 때 쓰는 편의 메서드. */
    public void record(CandidateSource source, List<Long> rankedItemIds, long groundTruthItemId) {
        record(Map.of(source, rankedItemIds), groundTruthItemId);
    }

    public void record(Map<CandidateSource, List<Long>> rankedItemIdsBySource, long groundTruthItemId) {
        record(UNSPECIFIED_STEP, rankedItemIdsBySource, groundTruthItemId);
    }

    /**
     * @param purchaseStep 코어를 몇 개 산 시점인지. 0코어와 4코어는 사실상 다른 문제라
     *                     단계를 뭉쳐서 평균 내면 표본이 많은 얕은 단계가 지표를 지배한다.
     */
    public void record(
            int purchaseStep, Map<CandidateSource, List<Long>> rankedItemIdsBySource, long groundTruthItemId
    ) {
        record(null, purchaseStep, rankedItemIdsBySource, groundTruthItemId);
    }

    /**
     * @param position 포지션별 분해용. Ally-Synergy는 "서포터는 아군 ADC에 따라 빌드가 갈린다"를
     *                 전제로 존재하므로, 전체 평균만 보면 그 전제를 검증할 수 없다.
     */
    public void record(
            ChampionPosition position,
            int purchaseStep,
            Map<CandidateSource, List<Long>> rankedItemIdsBySource,
            long groundTruthItemId
    ) {
        Map<CandidateSource, List<Long>> copied = new EnumMap<>(CandidateSource.class);
        rankedItemIdsBySource.forEach((source, itemIds) -> copied.put(source, List.copyOf(itemIds)));
        observations.add(new Observation(position, purchaseStep, copied, groundTruthItemId));
    }

    public double recallAt(CandidateSource source, int k) {
        return ratioOf(observation -> observation.foundBy(source, k));
    }

    public double unionRecallAt(int k) {
        return ratioOf(observation -> observation.foundByAny(k));
    }

    /** 그 generator만 찾은 정답의 비율. 0이면 다른 generator로 완전히 대체 가능하다는 뜻이다. */
    public double uniqueContributionOf(CandidateSource source, int k) {
        return ratioOf(observation -> observation.foundOnlyBy(source, k));
    }

    public int queryCount() {
        return observations.size();
    }

    public double recallAtStep(CandidateSource source, int k, int purchaseStep) {
        return ratioAtStep(purchaseStep, observation -> observation.foundBy(source, k));
    }

    public double unionRecallAtStep(int k, int purchaseStep) {
        return ratioAtStep(purchaseStep, observation -> observation.foundByAny(k));
    }

    public double uniqueContributionAtStep(CandidateSource source, int k, int purchaseStep) {
        return ratioAtStep(purchaseStep, observation -> observation.foundOnlyBy(source, k));
    }

    public double recallAtPosition(CandidateSource source, int k, ChampionPosition position) {
        return ratioAtPosition(position, observation -> observation.foundBy(source, k));
    }

    public double unionRecallAtPosition(int k, ChampionPosition position) {
        return ratioAtPosition(position, observation -> observation.foundByAny(k));
    }

    public double uniqueContributionAtPosition(CandidateSource source, int k, ChampionPosition position) {
        return ratioAtPosition(position, observation -> observation.foundOnlyBy(source, k));
    }

    public int queryCountAtPosition(ChampionPosition position) {
        return (int) observations.stream().filter(o -> o.position() == position).count();
    }

    private double ratioAtPosition(
            ChampionPosition position, java.util.function.Predicate<Observation> condition
    ) {
        List<Observation> atPosition = observations.stream()
                .filter(observation -> observation.position() == position)
                .toList();
        if (atPosition.isEmpty()) {
            return 0.0;
        }
        return (double) atPosition.stream().filter(condition).count() / atPosition.size();
    }

    public int queryCountAtStep(int purchaseStep) {
        return (int) observations.stream().filter(o -> o.purchaseStep() == purchaseStep).count();
    }

    /** 관측된 구매 단계를 오름차순으로. */
    public List<Integer> observedSteps() {
        return observations.stream()
                .map(Observation::purchaseStep)
                .distinct()
                .sorted()
                .toList();
    }

    private double ratioOf(java.util.function.Predicate<Observation> condition) {
        if (observations.isEmpty()) {
            return 0.0;
        }
        return (double) observations.stream().filter(condition).count() / observations.size();
    }

    private double ratioAtStep(int purchaseStep, java.util.function.Predicate<Observation> condition) {
        List<Observation> atStep = observations.stream()
                .filter(observation -> observation.purchaseStep() == purchaseStep)
                .toList();
        if (atStep.isEmpty()) {
            return 0.0;
        }
        return (double) atStep.stream().filter(condition).count() / atStep.size();
    }

    private record Observation(
            ChampionPosition position,
            int purchaseStep,
            Map<CandidateSource, List<Long>> rankedItemIdsBySource,
            long groundTruthItemId
    ) {

        private boolean foundBy(CandidateSource source, int k) {
            List<Long> ranked = rankedItemIdsBySource.get(source);
            if (ranked == null) {
                return false;
            }
            return ranked.stream().limit(k).anyMatch(itemId -> itemId == groundTruthItemId);
        }

        private boolean foundByAny(int k) {
            return rankedItemIdsBySource.keySet().stream().anyMatch(source -> foundBy(source, k));
        }

        private boolean foundOnlyBy(CandidateSource source, int k) {
            if (!foundBy(source, k)) {
                return false;
            }
            return rankedItemIdsBySource.keySet().stream()
                    .filter(other -> other != source)
                    .noneMatch(other -> foundBy(other, k));
        }
    }
}
