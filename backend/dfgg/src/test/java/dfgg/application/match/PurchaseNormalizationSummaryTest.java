package dfgg.application.match;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PurchaseNormalizationSummaryTest {

    @Test
    void 성공한_경기의_저장_수를_합하고_실패를_경기_ID와_함께_기록한다() {
        PurchaseNormalizationSummary summary = new PurchaseNormalizationSummary();

        summary.succeed(2);
        summary.succeed(3);
        summary.fail("KR_3", "Timeline 오류");
        PurchaseNormalizationResult result = summary.toResult(3);

        assertThat(result.processed()).isEqualTo(3);
        assertThat(result.succeeded()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.savedPurchases()).isEqualTo(5);
        assertThat(result.failures()).containsExactly("KR_3: Timeline 오류");
    }

    @Test
    void 배치_결과에_커서와_남은_경기_여부를_담는다() {
        PurchaseNormalizationSummary summary = new PurchaseNormalizationSummary();

        summary.succeed(1);
        PurchaseNormalizationBatchResult result = summary.toBatchResult(1, "KR_1", true);

        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.savedPurchases()).isEqualTo(1);
        assertThat(result.nextCursor()).isEqualTo("KR_1");
        assertThat(result.hasMore()).isTrue();
    }
}
