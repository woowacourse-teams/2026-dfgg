package dfgg.application.recommend.v3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dfgg.application.itemstats.ItemStatsAggregationService;
import dfgg.application.recommend.v3.feature.FeatureName;
import dfgg.application.recommend.v3.feature.FeatureVector;
import dfgg.application.recommend.v3.feature.StatsFeatureExtractor;
import dfgg.application.recommend.v3.generator.AllySynergyCandidateGenerator;
import dfgg.application.recommend.v3.generator.CounterCandidateGenerator;
import dfgg.application.recommend.v3.generator.PairLiftCalculator;
import dfgg.application.recommend.v3.generator.PairSynergyRetriever;
import dfgg.application.utils.WilsonScoreCalculator;
import dfgg.domain.champion.ChampionPosition;
import dfgg.infrastructure.config.TierScopeConfiguration;
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

/**
 * 요청한 포지션의 통계가 한 판도 없을 때(off-role) lift의 분모를 챔피언 전체(rollup)로 잡는다.
 * <p>
 * 분모가 0이면 lift 계산기가 모든 아이템에 1.0을 준다.
 * 그러면 ally 후보가 전부 동점이 되어 아이템 ID 순서로 나오는데,
 * 백오프 단계는 "조합 근거 있음"(TRIPLE)으로 남아 LTR이 근거 없는 목록을 강한 근거로 읽는다.
 * <p>
 * generator와 feature 추출기가 같은 분모를 봐야 한다.
 * 한쪽만 고치면 후보의 점수와 그 후보의 feature가 서로 다른 축에 선다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TierScopeConfiguration.class, ItemStatsAggregationService.class,
        AllySynergyCandidateGenerator.class, CounterCandidateGenerator.class, StatsFeatureExtractor.class,
        PairSynergyRetriever.class,  dfgg.application.recommend.v3.generator.ChampionBaselineReader.class,PairLiftCalculator.class, WilsonScoreCalculator.class})
@Sql("/sql/off-role-baseline-test-data.sql")
class OffRoleBaselineTest {

    private static final long JANNA = 40L;
    private static final long JINX = 222L;
    private static final long RAMMUS = 33L;

    private static final long ARDENT_CENSER = 3504L;         // 징크스와 함께일 때 평소의 2배
    private static final long SHURELYAS_BATTLESONG = 2065L;  // 징크스와 함께일 때 평소보다 덜 산다

    @Autowired
    private ItemStatsAggregationService aggregationService;
    @Autowired
    private AllySynergyCandidateGenerator allyGenerator;
    @Autowired
    private CounterCandidateGenerator counterGenerator;
    @Autowired
    private StatsFeatureExtractor statsFeatureExtractor;

    @BeforeEach
    void aggregate() {
        aggregationService.aggregate(1);
    }

    /** 잔나는 SUPPORT만 했다. MID로 요청하면 포지션 통계가 비어 있다. */
    private RecommendationQuery offRoleQuery() {
        return new RecommendationQuery(
                JANNA, ChampionPosition.MID, List.of(),
                List.of(JINX), List.of(RAMMUS),
                "EMERALD", "16.17");
    }

    private FeatureVector extract(long itemId) {
        FeatureVector vector = FeatureVector.empty();
        statsFeatureExtractor.extract(itemId, offRoleQuery(), vector);
        return vector;
    }

    private double scoreOf(GeneratorResult result, long itemId) {
        return result.rankedItems().stream()
                .filter(item -> item.itemId() == itemId)
                .findFirst()
                .orElseThrow()
                .score();
    }

    @Test
    @DisplayName("off-role이면 ally 후보를 챔피언 전체 대비 lift로 줄 세운다 — 아이템 ID 순서가 아니다")
    void generate_WhenPositionHasNoStats_RanksAllyCandidatesByRollupLift() {
        // given
        RecommendationQuery query = offRoleQuery();

        // when
        GeneratorResult result = allyGenerator.generate(query, 10);

        // then
        assertThat(result.rankedItems()).extracting(ScoredItem::itemId)
                .containsExactly(ARDENT_CENSER, SHURELYAS_BATTLESONG);
    }

    @Test
    @DisplayName("off-role이어도 ally 점수가 평소 대비 배율이다 — 전부 1.0으로 뭉개지지 않는다")
    void generate_WhenPositionHasNoStats_ScoresAllyLiftAgainstRollup() {
        // given
        RecommendationQuery query = offRoleQuery();

        // when
        GeneratorResult result = allyGenerator.generate(query, 10);

        // then: (50 + 0.25) / (100 + 1) / 0.25 ≈ 1.99
        assertThat(scoreOf(result, ARDENT_CENSER)).isCloseTo(1.99, within(0.01));
    }

    @Test
    @DisplayName("off-role이어도 ally lift feature가 generator 점수와 같다 — 같은 분모를 본다")
    void extract_WhenPositionHasNoStats_AllyFeatureMatchesGeneratorScore() {
        // given
        double generatorScore = scoreOf(allyGenerator.generate(offRoleQuery(), 10), ARDENT_CENSER);

        // when
        FeatureVector vector = extract(ARDENT_CENSER);

        // then
        assertThat(vector.get(FeatureName.ALLY_SCORE_MAX)).isCloseTo(generatorScore, within(1e-9));
    }

    @Test
    @DisplayName("off-role이어도 counter lift feature가 generator 점수와 같다 — generator만 rollup을 보지 않는다")
    void extract_WhenPositionHasNoStats_CounterFeatureMatchesGeneratorScore() {
        // given
        double generatorScore = scoreOf(counterGenerator.generate(offRoleQuery(), 10), ARDENT_CENSER);

        // when
        FeatureVector vector = extract(ARDENT_CENSER);

        // then
        assertThat(vector.get(FeatureName.COUNTER_LIFT_MAX)).isCloseTo(generatorScore, within(1e-9));
    }

    @Test
    @DisplayName("off-role이면 base rate feature도 챔피언 전체 값이다 — 향로 50/200")
    void extract_WhenPositionHasNoStats_SetsBaseRateFromRollup() {
        // when
        FeatureVector vector = extract(ARDENT_CENSER);

        // then
        assertThat(vector.get(FeatureName.CHAMPION_BASE_RATE_ALL)).isEqualTo(0.25);
    }
}
