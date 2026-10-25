package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class CollectionRunSummaryTest {

    @Test
    void 결과별_매치_수와_정규화_실패_수를_한_줄로_요약한다() {
        CollectionRunSummary summary = new CollectionRunSummary("EMERALD");
        summary.record(MatchCollectionOutcome.COLLECTED);
        summary.record(MatchCollectionOutcome.COLLECTED);
        summary.record(MatchCollectionOutcome.DUPLICATE);
        summary.recordRuneFailure();

        assertThat(summary.describe()).isEqualTo("tier=EMERALD, collected=2, duplicate=1, collectionFailed=0, "
                + "normalizationFailed=0, runeFailed=1, purchaseFailed=0");
    }

    @Test
    void 메트릭으로_내보낼_때_없던_결과도_0으로_등록한다() {
        CollectionRunSummary summary = new CollectionRunSummary("PLATINUM");
        summary.record(MatchCollectionOutcome.COLLECTED);
        summary.recordPurchaseFailure();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        summary.publishTo(registry);

        assertThat(registry.get(CollectionRunSummary.MATCHES_METRIC).counters()).hasSize(4);
        assertThat(registry.get(CollectionRunSummary.MATCHES_METRIC).tag("result", "collected").counter().count())
                .isEqualTo(1);
        assertThat(registry.get(CollectionRunSummary.MATCHES_METRIC).tag("result", "duplicate").counter().count())
                .isZero();
        assertThat(registry.get(CollectionRunSummary.NORMALIZATION_FAILURES_METRIC).tag("stage", "purchase")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get(CollectionRunSummary.NORMALIZATION_FAILURES_METRIC).tag("stage", "rune")
                .counter().count()).isZero();
    }
}
