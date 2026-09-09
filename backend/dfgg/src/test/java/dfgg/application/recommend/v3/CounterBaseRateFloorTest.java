package dfgg.application.recommend.v3;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.itemstats.ItemStatsAggregationService;
import dfgg.application.recommend.v3.generator.CounterCandidateGenerator;
import dfgg.application.recommend.v3.generator.CounterLiftCalculator;
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
 * counter 후보의 base rate 하한을 검증한다.
 * <p>
 * lift는 {@code P(item|나,적) / P(item|나)}라 분모가 바닥이면 분자가 조금만 커도 폭발한다.
 * 라플라스 보정까지 겹쳐 <b>한 번도 안 산 아이템</b>이 lift 수십~수백으로 상위를 점령하고,
 * 진짜 근거를 서빙 topK 밖으로 밀어냈다. 하한은 그 분모에 표본 조건을 거는 장치다.
 * <p>
 * 실측(스냅샷 2,312건): 하한 0% → 1%에서 Recall@5가 0.35% → 10.21%로 올랐고,
 * counter만 찾아내던 정답의 손실은 4건(0.17%p)이었다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ItemStatsAggregationService.class, TierScopeConfiguration.class})
@Sql("/sql/counter-base-rate-floor-test-data.sql")
class CounterBaseRateFloorTest {

    private static final long YASUO = 157L;
    private static final long RAMMUS = 33L;
    /** 야스오가 1,000판 중 300번 사는 아이템(30%). */
    private static final long COMMON_ITEM = 3031L;
    /** 야스오가 1,000판 중 2번(0.2%)만, 그것도 람머스전에서만 산 아이템. */
    private static final long FLUKE_ITEM = 3089L;

    @Autowired private ChampionPairItemStatsRepository pairRepository;
    @Autowired private ChampionItemStatsRepository championItemStatsRepository;
    @Autowired private ChampionItemRollupRepository championItemRollupRepository;
    @Autowired private ItemStatsAggregationService aggregationService;

    @Value("${recommendation.counter.minimum-base-rate}")
    private double configuredFloor;

    @BeforeEach
    void aggregate() {
        aggregationService.aggregate(1);
    }

    private CounterCandidateGenerator generatorWith(double floor) {
        return new CounterCandidateGenerator(
                pairRepository, championItemStatsRepository, championItemRollupRepository,
                new CounterLiftCalculator(1.0, 159), new WilsonScoreCalculator(), 1, floor);
    }

    private List<Long> candidates(double floor) {
        return generatorWith(floor).generate(query(), 20).rankedItems().stream()
                .map(ScoredItem::itemId)
                .toList();
    }

    private RecommendationQuery query() {
        return new RecommendationQuery(
                YASUO, ChampionPosition.MID, List.of(),
                List.of(), List.of(RAMMUS), "EMERALD", "16.17");
    }

    @Test
    @DisplayName("하한이 0이면 표본이 바닥인 아이템도 후보로 올라온다 — 하한 이전의 동작")
    void generate_WhenNoFloor_KeepsNeverPurchasedItem() {
        // when & then
        assertThat(candidates(0.0)).contains(FLUKE_ITEM);
    }

    @Test
    @DisplayName("하한을 걸면 그 챔피언이 거의 안 사는 아이템은 후보에서 빠진다")
    void generate_WhenBelowFloor_ExcludesRarelyPurchasedItem() {
        // when & then
        assertThat(candidates(0.01)).doesNotContain(FLUKE_ITEM);
    }

    @Test
    @DisplayName("하한을 걸어도 실제로 사는 아이템은 남는다 — 타입이 아니라 표본으로 거른다")
    void generate_WhenAboveFloor_KeepsCommonlyPurchasedItem() {
        // when & then
        assertThat(candidates(0.01)).contains(COMMON_ITEM);
    }

    @Test
    @DisplayName("하한을 구매율보다 높이면 그 아이템도 빠진다 — 경계가 값에 반응한다")
    void generate_WhenFloorAboveItsRate_ExcludesEvenCommonItem() {
        // when & then: 무한의 대검 구매율은 0.30이라 0.5 하한에 걸린다
        assertThat(candidates(0.5)).doesNotContain(COMMON_ITEM);
    }

    @Test
    @DisplayName("운영 기본값은 1%다 — 실측으로 정한 값이 설정에 실제로 들어가 있어야 한다")
    void configuration_HasOnePercentFloorByDefault() {
        // when & then
        assertThat(configuredFloor).isEqualTo(0.01);
    }
}
