package dfgg.application.recommend.v3;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.ChampionPositionNormalizer;
import dfgg.application.itemstats.ItemStatsAggregationService;
import dfgg.application.recommend.v3.generator.BuildBackoffLevel;
import dfgg.application.recommend.v3.generator.BuildCandidateGenerator;
import dfgg.application.utils.WilsonScoreCalculator;
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
@Import({BuildCandidateGenerator.class, ItemStatsAggregationService.class,
        ChampionPositionNormalizer.class, WilsonScoreCalculator.class})
@Sql("/sql/build-generator-test-data.sql")
class BuildCandidateGeneratorTest {

    private static final long KRAKEN = 6673L;
    private static final long INFINITY_EDGE = 3031L;
    private static final long SHIELDBOW = 3036L;
    private static final long BOOTS = 3006L;
    private static final long BLACK_CLEAVER = 3072L;
    private static final long SURGING_ITEM = 3153L;
    private static final long NEVER_SEEN = 88888L;

    private static final int WINDOW_SIZE = 1;

    @Autowired
    private BuildCandidateGenerator generator;

    @Autowired
    private ItemStatsAggregationService aggregationService;

    @BeforeEach
    void aggregate() {
        aggregationService.aggregate(WINDOW_SIZE);
    }

    private RecommendationQuery queryWith(List<Long> purchasedItemIds) {
        return new RecommendationQuery(
                157L, ChampionPosition.MID, purchasedItemIds,
                List.of(222L, 412L, 64L, 516L), List.of(33L, 103L, 51L, 89L, 60L),
                "EMERALD", "16.17"
        );
    }

    private List<Long> itemIdsOf(GeneratorResult result) {
        return result.rankedItems().stream().map(ScoredItem::itemId).toList();
    }

    @Test
    @DisplayName("정확한 구매 prefix 다음에 실제로 산 아이템을 후보로 낸다")
    void generate_WhenExactPrefixMatches_ReturnsItemsPurchasedNext() {
        // given: [크라켄, 무한의대검] 다음은 표본에서 3036(3판) 또는 3006(2판)
        GeneratorResult result = generator.generate(queryWith(List.of(KRAKEN, INFINITY_EDGE)), 10);

        // then
        assertThat(itemIdsOf(result)).containsExactlyInAnyOrder(SHIELDBOW, BOOTS);
    }

    @Test
    @DisplayName("표본이 많은 전개를 더 높은 순위로 낸다")
    void generate_WhenOneContinuationIsMoreCommon_RanksItHigher() {
        // when
        GeneratorResult result = generator.generate(queryWith(List.of(KRAKEN, INFINITY_EDGE)), 10);

        // then: 3036이 3판, 3006이 2판
        assertThat(itemIdsOf(result)).containsExactly(SHIELDBOW, BOOTS);
    }

    @Test
    @DisplayName("정확 prefix가 맞으면 백오프하지 않는다")
    void generate_WhenExactPrefixMatches_UsesExactPrefixLevel() {
        // when
        GeneratorResult result = generator.generate(queryWith(List.of(KRAKEN, INFINITY_EDGE)), 10);

        // then
        assertThat(result.backoffLevel()).isEqualTo(BuildBackoffLevel.EXACT_PREFIX.ordinal());
    }

    @Test
    @DisplayName("정확 prefix가 없으면 마지막 아이템 다음 전개로 백오프한다")
    void generate_WhenExactPrefixHasNoMatch_BacksOffToLastItemTransition() {
        // given: [88888, 6673]은 표본에 없지만 마지막 아이템 6673 다음은 관측된다
        GeneratorResult result = generator.generate(queryWith(List.of(NEVER_SEEN, KRAKEN)), 10);

        // then: 6673 뒤에 온 것은 3031(5판)과 3072(4판)
        assertThat(result.backoffLevel()).isEqualTo(BuildBackoffLevel.LAST_ITEM.ordinal());
        assertThat(itemIdsOf(result)).containsExactlyInAnyOrder(INFINITY_EDGE, BLACK_CLEAVER);
    }

    @Test
    @DisplayName("마지막 아이템 전개도 없으면 챔피언 단위 통계로 백오프한다")
    void generate_WhenNoTransitionMatches_BacksOffToChampionLevel() {
        // given: 한 번도 안 나온 아이템만 들고 있으면 전개를 찾을 수 없다
        GeneratorResult result = generator.generate(queryWith(List.of(NEVER_SEEN)), 10);

        // then
        assertThat(result.backoffLevel()).isEqualTo(BuildBackoffLevel.CHAMPION.ordinal());
        assertThat(itemIdsOf(result)).contains(KRAKEN, SURGING_ITEM);
    }

    @Test
    @DisplayName("아무것도 안 샀으면 첫 코어 분포를 낸다")
    void generate_WhenNothingPurchased_ReturnsFirstCoreDistribution() {
        // when
        GeneratorResult result = generator.generate(queryWith(List.of()), 10);

        // then: 1코어로 관측된 것들 — 6673(9판), 3072(12판), 3033(11판), 3153(10판), 3031(1판)
        assertThat(itemIdsOf(result)).contains(KRAKEN, BLACK_CLEAVER, SURGING_ITEM);
    }

    @Test
    @DisplayName("최근 급등 아이템이 전체 표본 기준으로 밀려도 후보에 남는다 — recent/all union의 목적")
    void generate_WhenItemSurgedRecently_IsRetrievedEvenIfWeakOverall() {
        // given: 3153은 16.17에서만 5판. topK를 2로 좁혀 전체 기준으로는 잘리게 만든다.
        GeneratorResult result = generator.generate(queryWith(List.of(NEVER_SEEN)), 2);

        // then: all 기준 top-2에 못 들어도 recent 기준으로 올라와 살아남아야 한다
        assertThat(itemIdsOf(result)).contains(SURGING_ITEM);
    }

    @Test
    @DisplayName("topK를 넘는 후보는 내지 않는다")
    void generate_WhenMoreCandidatesThanTopK_LimitsResultSize() {
        // when
        GeneratorResult result = generator.generate(queryWith(List.of(NEVER_SEEN)), 2);

        // then: recent/all union이라 최대 2*topK지만 최종적으로는 topK로 자른다
        assertThat(result.rankedItems()).hasSizeLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("source는 BUILD다")
    void source_IsBuild() {
        assertThat(generator.source()).isEqualTo(CandidateSource.BUILD);
    }
}
