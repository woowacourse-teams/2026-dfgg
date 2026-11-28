package dfgg.application.recommend.v3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dfgg.application.itemstats.ItemStatsAggregationService;
import dfgg.application.recommend.v3.generator.PairLiftCalculator;
import dfgg.application.recommend.v3.generator.PairSynergyRetriever;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.itemstats.ChampionItemStats;
import dfgg.domain.itemstats.ChampionItemStatsRepository;
import dfgg.domain.itemstats.ChampionPairItemStatsRepository;
import dfgg.domain.itemstats.PairRelation;
import dfgg.infrastructure.config.TierScopeConfiguration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * 승률 lift가 아군 자체의 강함을 상쇄하는지 검증한다.
 * <p>
 * 조합은 세지만(0.70) 아이템은 그 안에서 평범하다(0.70).
 * 아이템 품질만 통제하면 1.40으로 부풀고, 아군 품질까지 통제하면 1.00이다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ItemStatsAggregationService.class, TierScopeConfiguration.class})
@Sql("/sql/ally-win-lift-test-data.sql")
class AllyWinLiftTest {

    private static final long JANNA = 40L;
    private static final long JINX = 222L;
    private static final long ARDENT_CENSER = 3504L;
    private static final int MINIMUM_SAMPLES = 1;

    @Autowired private ChampionPairItemStatsRepository pairRepository;
    @Autowired private ChampionItemStatsRepository championItemStatsRepository;
    @Autowired private ItemStatsAggregationService aggregationService;

    private PairSynergyRetriever retriever;

    @BeforeEach
    void setUp() {
        aggregationService.aggregate(1);
        retriever = new PairSynergyRetriever(
                pairRepository, new PairLiftCalculator(1.0, 159), 1, 0.0);
    }

    @Test
    @DisplayName("조합이 세도 아이템이 그 안에서 평범하면 승률 lift는 1이다 — 아군 효과가 상쇄된다")
    void winLiftsByItem_WhenAllyIsStrongButItemIsAverageWithinPair_IsNeutral() {
        // given: 조합 승률 0.70, 그 안에서 향로도 0.70, 향로의 평소 승률 0.50, 잔나 기준 0.50
        // when
        Map<Long, Map<Long, Double>> winLifts = retriever.winLiftsByItem(
                JANNA, List.of(JINX), PairRelation.ALLY, itemWinRates(), championWinRate(), MINIMUM_SAMPLES);

        // then: 통제 없이 계산하면 0.70/0.50 = 1.40이 나온다
        assertThat(winLifts.get(ARDENT_CENSER).get(JINX)).isCloseTo(1.0, within(0.02));
    }

    @Test
    @DisplayName("표본이 하한에 못 미치면 아예 내지 않는다 — 승패는 이항이라 얇으면 튄다")
    void winLiftsByItem_WhenBelowMinimumSamples_OmitsTheEntry() {
        // when
        Map<Long, Map<Long, Double>> winLifts = retriever.winLiftsByItem(
                JANNA, List.of(JINX), PairRelation.ALLY, itemWinRates(), championWinRate(), 1000);

        // then
        assertThat(winLifts).isEmpty();
    }

    /** 잔나의 아이템 무관 승률 — 픽스처상 100판 50승이라 0.50이다. */
    private double championWinRate() {
        return championItemStatsRepository
                .findByChampionIdAndPosition(Math.toIntExact(JANNA), ChampionPosition.SUPPORT)
                .stream()
                .filter(stats -> stats.getChampionGameCountAll() > 0)
                .mapToDouble(stats ->
                        (double) stats.getChampionWinCountAll() / stats.getChampionGameCountAll())
                .findFirst().orElse(0.0);
    }

    private Map<Long, Double> itemWinRates() {
        Map<Long, Double> winRateByItem = new HashMap<>();
        for (ChampionItemStats stats : championItemStatsRepository
                .findByChampionIdAndPosition(Math.toIntExact(JANNA), ChampionPosition.SUPPORT)) {
            if (stats.getPurchaseCountAll() > 0) {
                winRateByItem.put(stats.getItemId(),
                        (double) stats.getWinCountAll() / stats.getPurchaseCountAll());
            }
        }
        return winRateByItem;
    }
}
