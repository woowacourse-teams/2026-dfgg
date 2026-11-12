package dfgg.application;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;

/**
 * 수집 실행 한 번에서 매치별 결과와 룬·구매 정규화 실패를 센다.
 * 실행이 끝나면 요약 로그 한 줄과 누적 메트릭으로 내보낸다.
 */
final class CollectionRunSummary {

    // Prometheus로 나갈 때 점은 밑줄로 바뀌고 카운터라 _total이 붙는다(riot_collection_matches_total).
    static final String MATCHES_METRIC = "riot.collection.matches";
    static final String NORMALIZATION_FAILURES_METRIC = "riot.collection.normalization.failures";
    private static final String RUNE_STAGE = "rune";
    private static final String PURCHASE_STAGE = "purchase";

    private final String tier;
    // 결과 종류가 enum으로 정해져 있어 EnumMap을 쓴다. 한 번도 기록되지 않은 결과는 키가 없고 0으로 센다.
    private final Map<MatchCollectionOutcome, Integer> matchesByOutcome = new EnumMap<>(MatchCollectionOutcome.class);
    // 룬·구매 실패는 COLLECTED 매치에서만 생기는 별도 단계라 매치 결과와 따로 센다.
    private int runeFailures;
    private int purchaseFailures;

    CollectionRunSummary(String tier) {
        this.tier = tier;
    }

    /** 매치 하나의 처리 결과를 더한다. 같은 결과가 이미 있으면 1을 더하고, 없으면 1로 시작한다. */
    void record(MatchCollectionOutcome outcome) {
        matchesByOutcome.merge(outcome, 1, Integer::sum);
    }

    void recordRuneFailure() {
        runeFailures++;
    }

    void recordPurchaseFailure() {
        purchaseFailures++;
    }

    /**
     * 이번 실행의 숫자를 누적 카운터에 더한다. 같은 이름·태그면 registry가 같은 카운터를 돌려주므로 실행마다 쌓인다.
     * 0인 결과도 증가시켜 Prometheus에 시계열이 처음부터 생기게 한다.
     */
    void publishTo(MeterRegistry registry) {
        for (MatchCollectionOutcome outcome : MatchCollectionOutcome.values()) {
            registry.counter(MATCHES_METRIC, "result", outcome.tag()).increment(count(outcome));
        }
        registry.counter(NORMALIZATION_FAILURES_METRIC, "stage", RUNE_STAGE).increment(runeFailures);
        registry.counter(NORMALIZATION_FAILURES_METRIC, "stage", PURCHASE_STAGE).increment(purchaseFailures);
    }

    /** 실행 요약 로그에 쓰는 한 줄. 메트릭과 달리 이번 실행의 숫자만 담는다. */
    String describe() {
        return "tier=" + tier
                + ", collected=" + count(MatchCollectionOutcome.COLLECTED)
                + ", duplicate=" + count(MatchCollectionOutcome.DUPLICATE)
                + ", collectionFailed=" + count(MatchCollectionOutcome.COLLECTION_FAILED)
                + ", normalizationFailed=" + count(MatchCollectionOutcome.NORMALIZATION_FAILED)
                + ", runeFailed=" + runeFailures
                + ", purchaseFailed=" + purchaseFailures;
    }

    private int count(MatchCollectionOutcome outcome) {
        return matchesByOutcome.getOrDefault(outcome, 0);
    }
}
