package dfgg.application.recommend.v3;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.itemstats.ItemStatsAggregationService;
import dfgg.application.recommend.v3.generator.AllySynergyCandidateGenerator;
import dfgg.application.recommend.v3.generator.PairLiftCalculator;
import dfgg.application.recommend.v3.generator.PairSynergyRetriever;
import dfgg.application.utils.WilsonScoreCalculator;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.itemstats.ChampionItemRollupRepository;
import dfgg.domain.itemstats.ChampionItemStatsRepository;
import dfgg.domain.itemstats.ChampionPairItemStatsRepository;
import dfgg.infrastructure.config.TierScopeConfiguration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

/**
 * ally 후보의 base rate 하한을 검증한다.
 * <p>
 * ally 점수를 lift로 바꾸면서 counter가 겪던 병폐를 그대로 물려받았다 — 실측에서 ally 상위 5 후보의 96.9%가 구매율 1% 미만이었고 lift 최대는 7749였다.
 * 분모가 바닥이면 분자가 조금만 커도 lift가 폭발하기 때문이다.
 * {@code AllyEvidence}의 {@code lift > 1} 문턱은 그 상태에서 아무것도 거르지 못한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ItemStatsAggregationService.class, TierScopeConfiguration.class})
@Sql("/sql/ally-base-rate-floor-test-data.sql")
class AllyBaseRateFloorTest {

    private static final long JANNA = 40L;
    private static final long JINX = 222L;
    /**
     * 잔나가 1,000판 중 300번 사는 아이템(30%).
     */
    private static final long MOONSTONE = 6617L;
    /**
     * 잔나가 1,000판 중 2번(0.2%)만, 그것도 징크스와 함께일 때만 산 아이템.
     */
    private static final long FLUKE_ITEM = 3089L;

    @Autowired
    private ChampionPairItemStatsRepository pairRepository;
    @Autowired
    private ChampionItemStatsRepository championItemStatsRepository;
    @Autowired
    private ChampionItemRollupRepository championItemRollupRepository;
    @Autowired
    private ItemStatsAggregationService aggregationService;

    @Value("${recommendation.ally-synergy.minimum-base-rate}")
    private double configuredFloor;

    @BeforeEach
    void aggregate() {
        aggregationService.aggregate(1);
    }

    private List<Long> candidates(double floor) {
        PairSynergyRetriever retriever = new PairSynergyRetriever(
                pairRepository, new PairLiftCalculator(1.0, 159), 1, floor);
        AllySynergyCandidateGenerator generator = new AllySynergyCandidateGenerator(
                retriever, championItemStatsRepository, championItemRollupRepository,
                new WilsonScoreCalculator());
        return generator.generate(query(), 20).rankedItems().stream()
                .map(ScoredItem::itemId)
                .toList();
    }

    private RecommendationQuery query() {
        return new RecommendationQuery(
                JANNA, ChampionPosition.SUPPORT, List.of(),
                List.of(JINX), List.of(), "EMERALD", "16.17");
    }

    @Test
    @DisplayName("하한이 0이면 표본이 바닥인 아이템도 후보로 올라온다 — 하한 이전의 동작")
    void generate_WhenNoFloor_KeepsRarelyPurchasedItem() {
        assertThat(candidates(0.0)).contains(FLUKE_ITEM);
    }

    @Test
    @DisplayName("하한을 걸면 그 챔피언이 거의 안 사는 아이템은 후보에서 빠진다")
    void generate_WhenBelowFloor_ExcludesRarelyPurchasedItem() {
        assertThat(candidates(0.01)).doesNotContain(FLUKE_ITEM);
    }

    @Test
    @DisplayName("하한을 걸어도 실제로 사는 아이템은 남는다")
    void generate_WhenAboveFloor_KeepsCommonlyPurchasedItem() {
        assertThat(candidates(0.01)).contains(MOONSTONE);
    }

    @Test
    @DisplayName("하한을 구매율보다 높이면 그 아이템도 빠진다 — 경계가 값에 반응한다")
    void generate_WhenFloorAboveItsRate_ExcludesEvenCommonItem() {
        assertThat(candidates(0.5)).doesNotContain(MOONSTONE);
    }

    @Test
    @DisplayName("운영 기본값은 2%다 — counter(1%)와 다르게 실측으로 따로 정했다")
    void configuration_HasTwoPercentFloorByDefault() {
        // 하한을 올릴수록 ally가 BUILD를 베낀다.
        assertThat(configuredFloor).isEqualTo(0.02);
    }
}
