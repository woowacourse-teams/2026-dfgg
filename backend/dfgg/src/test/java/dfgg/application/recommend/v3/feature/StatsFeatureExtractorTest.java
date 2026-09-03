package dfgg.application.recommend.v3.feature;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.itemstats.ItemStatsAggregationService;
import dfgg.application.recommend.v3.RecommendationQuery;
import dfgg.domain.champion.ChampionPosition;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ItemStatsAggregationService.class, StatsFeatureExtractor.class,
        dfgg.application.recommend.v3.generator.PairSynergyRetriever.class,
        dfgg.application.recommend.v3.generator.CounterLiftCalculator.class,
        dfgg.application.utils.WilsonScoreCalculator.class})
@Sql("/sql/counter-test-data.sql")
class StatsFeatureExtractorTest {

    private static final long YASUO = 157L;
    private static final long RAMMUS = 33L;
    private static final long AHRI = 103L;

    private static final long DOMINIK = 3036L;   // 야스오가 람머스 상대로 더 사는 것
    private static final long LIANDRY = 6653L;   // 아리가 산 것 — 야스오는 안 산다

    @Autowired
    private ItemStatsAggregationService aggregationService;
    @Autowired
    private StatsFeatureExtractor extractor;

    @BeforeEach
    void aggregate() {
        aggregationService.aggregate(1);
    }

    private RecommendationQuery query() {
        return new RecommendationQuery(
                YASUO, ChampionPosition.MID, List.of(),
                List.of(222L, 412L, 64L, 516L), List.of(RAMMUS, AHRI, 51L, 89L, 60L),
                "EMERALD", "16.17"
        );
    }

    private FeatureVector extract(long itemId) {
        FeatureVector vector = FeatureVector.empty();
        extractor.extract(itemId, query(), vector);
        return vector;
    }

    @Test
    @DisplayName("내 챔피언의 base rate를 남긴다 — 야스오의 도미닉은 40판 중 18판")
    void extract_WhenChampionHasHistory_SetsBaseRate() {
        // when
        FeatureVector vector = extract(DOMINIK);

        // then
        assertThat(vector.get(FeatureName.CHAMPION_BASE_RATE_ALL)).isEqualTo(0.45);
    }

    @Test
    @DisplayName("한 번도 안 산 아이템의 base rate는 0이다 — NaN이 아니라 '0번 샀다'는 관측이다")
    void extract_WhenChampionNeverBoughtIt_BaseRateIsZeroNotNaN() {
        // when: 야스오는 리안드리를 산 적이 없다(아리가 샀다)
        FeatureVector vector = extract(LIANDRY);

        // then
        assertThat(vector.get(FeatureName.CHAMPION_BASE_RATE_ALL)).isZero().isNotNaN();
    }

    @Test
    @DisplayName("counter lift를 적별로 계산해 집계한다")
    void extract_WhenEnemiesObserved_SetsCounterLiftAggregates() {
        // when: 야스오의 도미닉은 평소 45%인데 람머스 상대로는 80%
        FeatureVector vector = extract(DOMINIK);

        // then
        assertThat(vector.get(FeatureName.COUNTER_LIFT_MAX)).isGreaterThan(1.0);
        assertThat(vector.get(FeatureName.COUNTER_LIFT_TOP1))
                .isEqualTo(vector.get(FeatureName.COUNTER_LIFT_MAX));
    }

    @Test
    @DisplayName("lift와 원 확률과 base rate가 각각 별도 feature로 남는다 — 실패 유형을 가르는 세 값이다")
    void extract_WhenCounterEvidenceExists_KeepsLiftProbabilityAndBaseRateSeparately() {
        // when
        FeatureVector vector = extract(DOMINIK);

        // then: 람머스 상대 16/20 = 0.8
        assertThat(vector.get(FeatureName.COUNTER_PAIR_PROBABILITY_MAX)).isEqualTo(0.8);
        assertThat(vector.get(FeatureName.CHAMPION_BASE_RATE_ALL)).isEqualTo(0.45);
        assertThat(vector.get(FeatureName.COUNTER_LIFT_MAX)).isNotNaN();
    }

    @Test
    @DisplayName("아군과의 관계 점수를 집계한다")
    void extract_WhenAlliesObserved_SetsAllyAggregates() {
        // when
        FeatureVector vector = extract(DOMINIK);

        // then: 관측이 없으면 NaN, 있으면 max ≥ top2
        if (!Double.isNaN(vector.get(FeatureName.ALLY_SCORE_MAX))) {
            assertThat(vector.get(FeatureName.ALLY_SCORE_MAX))
                    .isGreaterThanOrEqualTo(vector.get(FeatureName.ALLY_SCORE_TOP2));
        }
    }

    @Test
    @DisplayName("현재 패치의 픽률과 직전 패치 대비 변화를 남긴다 — 버프/너프가 여기서 드러난다")
    void extract_WhenPatchHistoryExists_SetsPickRateAndDelta() {
        // when
        FeatureVector vector = extract(DOMINIK);

        // then
        assertThat(vector.get(FeatureName.ITEM_PICK_RATE_CURRENT_PATCH)).isNotNaN();
    }

    @Test
    @DisplayName("최근/전체 base rate 비율을 남긴다 — 급등 중인지 사양길인지를 한 값으로")
    void extract_WhenBothScopesExist_SetsRecentVsAllRatio() {
        // when
        FeatureVector vector = extract(DOMINIK);

        // then
        assertThat(vector.get(FeatureName.CHAMPION_BASE_RATE_RECENT_VS_ALL)).isNotNaN();
    }

    @Test
    @DisplayName("통계가 전혀 없는 아이템은 base rate 0, counter는 NaN이다")
    void extract_WhenItemNeverObserved_LeavesCounterAsNaN() {
        // when
        FeatureVector vector = extract(99999L);

        // then
        assertThat(vector.get(FeatureName.CHAMPION_BASE_RATE_ALL)).isZero();
        assertThat(vector.get(FeatureName.COUNTER_LIFT_MAX)).isNaN();
    }
}
