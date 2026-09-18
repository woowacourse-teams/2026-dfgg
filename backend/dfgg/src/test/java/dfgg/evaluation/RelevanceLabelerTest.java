package dfgg.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RelevanceLabelerTest {

    private static final long NEXT_PURCHASE = 3031L;   // 이 시점에 실제로 산 것
    private static final long LATER_PURCHASE = 3036L;  // 같은 게임에서 나중에 산 것
    private static final long POPULAR_ALTERNATIVE = 6673L; // 이 챔피언이 흔히 사지만 이 판엔 없던 것
    private static final long RARE_ITEM = 3157L;       // 이 챔피언이 거의 안 사는 것

    /** 이 챔피언 게임의 5% 이상에서 나오면 "그럴듯한 대안"으로 본다. */
    private static final double PLAUSIBLE_ALTERNATIVE_THRESHOLD = 0.05;

    private final RelevanceLabeler labeler = new RelevanceLabeler(PLAUSIBLE_ALTERNATIVE_THRESHOLD);

    private RelevanceLabeler.Context context() {
        return new RelevanceLabeler.Context(
                NEXT_PURCHASE,
                List.of(LATER_PURCHASE),
                Map.of(POPULAR_ALTERNATIVE, 0.42, RARE_ITEM, 0.001, NEXT_PURCHASE, 0.30)
        );
    }

    @Test
    @DisplayName("실제로 다음에 산 아이템이 최고 등급이다")
    void label_WhenActualNextPurchase_IsHighest() {
        assertThat(labeler.label(NEXT_PURCHASE, context())).isEqualTo(3);
    }

    @Test
    @DisplayName("같은 게임에서 나중에 산 아이템은 그다음 등급이다 — 지금은 아니지만 결국 산 선택이다")
    void label_WhenPurchasedLaterInSameGame_IsSecondHighest() {
        assertThat(labeler.label(LATER_PURCHASE, context())).isEqualTo(2);
    }

    @Test
    @DisplayName("이 판엔 없지만 이 챔피언이 흔히 사는 아이템은 '그럴듯한 대안'이다 — 안 샀다고 나쁜 게 아니다")
    void label_WhenCommonForChampionButNotInThisBuild_IsPlausibleAlternative() {
        assertThat(labeler.label(POPULAR_ALTERNATIVE, context())).isEqualTo(1);
    }

    @Test
    @DisplayName("이 챔피언이 거의 안 사는 아이템만 0이다")
    void label_WhenRarelyBoughtByChampion_IsZero() {
        assertThat(labeler.label(RARE_ITEM, context())).isZero();
    }

    @Test
    @DisplayName("구매 이력이 전혀 없는 아이템은 0이다")
    void label_WhenNeverObservedForChampion_IsZero() {
        assertThat(labeler.label(99999L, context())).isZero();
    }

    @Test
    @DisplayName("임계값에 걸치면 대안으로 본다")
    void label_WhenExactlyAtThreshold_IsPlausibleAlternative() {
        RelevanceLabeler.Context borderline = new RelevanceLabeler.Context(
                NEXT_PURCHASE, List.of(), Map.of(RARE_ITEM, PLAUSIBLE_ALTERNATIVE_THRESHOLD));

        assertThat(labeler.label(RARE_ITEM, borderline)).isEqualTo(1);
    }

    @Test
    @DisplayName("정답이 base rate가 낮아도 최고 등급을 유지한다 — 실제 관측이 통계보다 우선한다")
    void label_WhenGroundTruthIsRareForChampion_StillHighest() {
        RelevanceLabeler.Context rareGroundTruth = new RelevanceLabeler.Context(
                RARE_ITEM, List.of(), Map.of(RARE_ITEM, 0.001));

        assertThat(labeler.label(RARE_ITEM, rareGroundTruth)).isEqualTo(3);
    }

    @Test
    @DisplayName("나중에 산 아이템이 base rate가 낮아도 등급 2를 유지한다")
    void label_WhenLaterPurchaseIsRare_StillSecondHighest() {
        RelevanceLabeler.Context rareLater = new RelevanceLabeler.Context(
                NEXT_PURCHASE, List.of(RARE_ITEM), Map.of(RARE_ITEM, 0.001));

        assertThat(labeler.label(RARE_ITEM, rareLater)).isEqualTo(2);
    }

    @Test
    @DisplayName("한 query에 최고 등급은 정확히 하나다")
    void label_WhenLabelingManyCandidates_ExactlyOneIsHighest() {
        // given
        List<Long> candidates = List.of(NEXT_PURCHASE, LATER_PURCHASE, POPULAR_ALTERNATIVE, RARE_ITEM);

        // when
        long highestCount = candidates.stream()
                .filter(itemId -> labeler.label(itemId, context()) == RelevanceLabeler.GROUND_TRUTH_LABEL)
                .count();

        // then
        assertThat(highestCount).isEqualTo(1);
    }
}
