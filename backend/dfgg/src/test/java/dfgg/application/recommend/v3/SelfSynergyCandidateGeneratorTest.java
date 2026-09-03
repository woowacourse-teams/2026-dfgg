package dfgg.application.recommend.v3;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.itemstats.ItemStatsAggregationService;
import dfgg.application.recommend.v3.generator.SelfSynergyBackoffLevel;
import dfgg.application.recommend.v3.generator.SelfSynergyCandidateGenerator;
import dfgg.application.utils.WilsonScoreCalculator;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.itemstats.ChampionItemRollupRepository;
import dfgg.domain.itemstats.ChampionItemStatsRepository;
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
@Import(ItemStatsAggregationService.class)
@Sql("/sql/self-synergy-test-data.sql")
class SelfSynergyCandidateGeneratorTest {

    private static final long KRAKEN = 6673L;
    private static final long INFINITY_EDGE = 3031L;
    private static final long OLD_ITEM = 3072L;
    private static final long RARE_ITEM = 6672L;
    private static final long THIN_POSITION_ITEM = 3078L;
    private static final long ROLLUP_ITEM = 3157L;

    private static final int MINIMUM_POSITION_SAMPLE = 10;

    @Autowired
    private ItemStatsAggregationService aggregationService;
    @Autowired
    private ChampionItemStatsRepository championItemStatsRepository;
    @Autowired
    private ChampionItemRollupRepository championItemRollupRepository;

    private SelfSynergyCandidateGenerator generator;

    @BeforeEach
    void setUp() {
        aggregationService.aggregate(1);
        generator = new SelfSynergyCandidateGenerator(
                championItemStatsRepository, championItemRollupRepository,
                new WilsonScoreCalculator(), MINIMUM_POSITION_SAMPLE
        );
    }

    private RecommendationQuery queryFor(long championId, ChampionPosition position, List<Long> purchased) {
        return new RecommendationQuery(
                championId, position, purchased,
                List.of(222L, 412L, 64L, 516L), List.of(33L, 103L, 51L, 89L, 60L),
                "EMERALD", "16.17"
        );
    }

    private List<Long> itemIdsOf(GeneratorResult result) {
        return result.rankedItems().stream().map(ScoredItem::itemId).toList();
    }

    @Test
    @DisplayName("점수는 구매 이력과 무관하다 — 이미 산 것을 뺀 나머지의 순서가 그대로 유지된다")
    void generate_WhenPurchaseHistoryDiffers_KeepsRelativeOrderOfRemainingItems() {
        // given: 같은 챔피언·포지션, 구매 이력만 다르다.
        //        Build와 갈리는 지점은 "현재 build를 보고 점수를 바꾸지 않는다"이지,
        //        "이미 산 아이템을 후보로 낸다"가 아니다.
        List<Long> purchased = List.of(KRAKEN, INFINITY_EDGE);
        List<Long> nothingPurchased =
                itemIdsOf(generator.generate(queryFor(157L, ChampionPosition.MID, List.of()), 10));
        List<Long> deepIntoBuild =
                itemIdsOf(generator.generate(queryFor(157L, ChampionPosition.MID, purchased), 10));

        // then: 구매한 것만 빠지고 나머지 순서는 그대로
        assertThat(deepIntoBuild)
                .isEqualTo(nothingPurchased.stream().filter(id -> !purchased.contains(id)).toList());
    }

    @Test
    @DisplayName("이미 산 아이템은 후보로 내지 않는다 — 살 수 없는 것으로 topK 예산을 쓰면 낭비다")
    void generate_WhenItemsAlreadyPurchased_ExcludesThemFromCandidates() {
        // given: 야스오가 가장 많이 사는 6673과 3072를 이미 샀다.
        //        이걸 후보로 내면 topK가 작을수록 실제 살 수 있는 후보가 밀려난다.
        GeneratorResult result = generator.generate(
                queryFor(157L, ChampionPosition.MID, List.of(KRAKEN, OLD_ITEM)), 3);

        // then
        assertThat(itemIdsOf(result)).doesNotContain(KRAKEN, OLD_ITEM);
    }

    @Test
    @DisplayName("챔피언이 자주 사는 아이템을 후보로 낸다")
    void generate_WhenChampionHasPurchaseHistory_ReturnsFrequentlyBoughtItems() {
        // when
        GeneratorResult result = generator.generate(queryFor(157L, ChampionPosition.MID, List.of()), 10);

        // then
        assertThat(itemIdsOf(result)).contains(KRAKEN, INFINITY_EDGE, OLD_ITEM);
    }

    @Test
    @DisplayName("표본이 적은 아이템은 Wilson 하한에 눌려 고빈도 아이템보다 아래로 간다")
    void generate_WhenSampleIsSmall_RanksBelowHighFrequencyItems() {
        // given: 6672는 20판 중 2판뿐
        GeneratorResult result = generator.generate(queryFor(157L, ChampionPosition.MID, List.of()), 10);

        // then
        List<Long> ranked = itemIdsOf(result);
        assertThat(ranked.indexOf(RARE_ITEM)).isGreaterThan(ranked.indexOf(KRAKEN));
    }

    @Test
    @DisplayName("최근 편중 아이템이 전체 표본 기준으로 밀려도 후보에 남는다")
    void generate_WhenItemIsRecentHeavy_SurvivesEvenIfWeakOverall() {
        // given: topK=1이면 전체 기준으로는 3072만 남는다(10/20 vs 6673 8/20)
        GeneratorResult result = generator.generate(queryFor(157L, ChampionPosition.MID, List.of()), 1);

        // then: 최근 기준 1위인 6673이 union으로 올라와 최종 순위를 가져간다
        assertThat(itemIdsOf(result)).containsExactly(KRAKEN);
    }

    @Test
    @DisplayName("포지션 표본이 부족하면 포지션을 합친 통계로 백오프한다")
    void generate_WhenPositionSampleIsTooThin_BacksOffToChampionRollup() {
        // given: 888은 TOP이 2판뿐이고 MIDDLE에 20판이 쌓여있다
        GeneratorResult result = generator.generate(queryFor(888L, ChampionPosition.TOP, List.of()), 10);

        // then
        assertThat(result.backoffLevel()).isEqualTo(SelfSynergyBackoffLevel.CHAMPION_ROLLUP.ordinal());
        assertThat(itemIdsOf(result)).contains(ROLLUP_ITEM, THIN_POSITION_ITEM);
    }

    @Test
    @DisplayName("포지션 표본이 충분하면 백오프하지 않는다")
    void generate_WhenPositionSampleIsSufficient_UsesPositionLevel() {
        // when: 157 MID는 20판
        GeneratorResult result = generator.generate(queryFor(157L, ChampionPosition.MID, List.of()), 10);

        // then
        assertThat(result.backoffLevel()).isEqualTo(SelfSynergyBackoffLevel.CHAMPION_POSITION.ordinal());
    }

    @Test
    @DisplayName("데이터가 전혀 없는 챔피언에게는 빈 결과를 낸다")
    void generate_WhenChampionHasNoData_ReturnsEmptyResult() {
        // when
        GeneratorResult result = generator.generate(queryFor(99999L, ChampionPosition.TOP, List.of()), 10);

        // then
        assertThat(result.rankedItems()).isEmpty();
    }

    @Test
    @DisplayName("topK를 넘는 후보는 내지 않는다")
    void generate_WhenMoreCandidatesThanTopK_LimitsResultSize() {
        assertThat(generator.generate(queryFor(157L, ChampionPosition.MID, List.of()), 2).rankedItems())
                .hasSizeLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("source는 SELF_SYNERGY다")
    void source_IsSelfSynergy() {
        assertThat(generator.source()).isEqualTo(CandidateSource.SELF_SYNERGY);
    }
}
