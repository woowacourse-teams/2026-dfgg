package dfgg.application.recommend.v3;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.itemstats.ItemStatsAggregationService;
import dfgg.application.recommend.v3.generator.AllyScoreAggregate;
import dfgg.application.recommend.v3.generator.AllySynergyCandidateGenerator;
import dfgg.application.recommend.v3.generator.PairBackoffLevel;
import dfgg.application.recommend.v3.generator.PairSynergyRetriever;
import dfgg.application.utils.WilsonScoreCalculator;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.itemstats.ChampionItemRollupRepository;
import dfgg.domain.itemstats.ChampionItemStatsRepository;
import dfgg.domain.itemstats.ChampionPairItemStatsRepository;
import dfgg.domain.itemstats.PairRelation;
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

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ItemStatsAggregationService.class)
@Sql("/sql/ally-synergy-test-data.sql")
class AllySynergyCandidateGeneratorTest {

    private static final long JANNA = 40L;
    private static final long JINX = 222L;
    private static final long KOGMAW = 96L;
    private static final long ORNN = 516L;
    private static final long AHRI = 103L;

    private static final long INCENSE = 3504L;   // 불타는 향로 — 징크스와 함께일 때
    private static final long MOONSTONE = 6617L; // 월석 재생기 — 코그모와 함께일 때
    private static final long MIKAEL = 3222L;
    private static final long LOCKET = 3190L;    // 오른과 1판 (지지도 부족)

    private static final int MINIMUM_PAIR_GAMES = 5;

    @Autowired
    private ItemStatsAggregationService aggregationService;
    @Autowired
    private ChampionPairItemStatsRepository pairRepository;
    @Autowired
    private ChampionItemStatsRepository championItemStatsRepository;
    @Autowired
    private ChampionItemRollupRepository championItemRollupRepository;

    private PairSynergyRetriever retriever;
    private AllySynergyCandidateGenerator generator;

    @BeforeEach
    void setUp() {
        aggregationService.aggregate(1);
        retriever = new PairSynergyRetriever(pairRepository, new WilsonScoreCalculator(), MINIMUM_PAIR_GAMES);
        generator = new AllySynergyCandidateGenerator(
                retriever, championItemStatsRepository, championItemRollupRepository,
                new WilsonScoreCalculator()
        );
    }

    private RecommendationQuery queryWithAllies(List<Long> allyChampionIds) {
        return new RecommendationQuery(
                JANNA, ChampionPosition.SUPPORT, List.of(),
                allyChampionIds, List.of(33L, 103L, 51L, 89L, 60L),
                "EMERALD", "16.17"
        );
    }

    private List<Long> itemIdsOf(GeneratorResult result) {
        return result.rankedItems().stream().map(ScoredItem::itemId).toList();
    }

    @Test
    @DisplayName("아군 각각에 대해 따로 조회하고 결과를 union한다")
    void generate_WhenMultipleAllies_UnionsPerAllyCandidates() {
        // given: 향로는 징크스와, 월석은 코그모와 함께일 때 나온 아이템이다
        GeneratorResult result = generator.generate(queryWithAllies(List.of(JINX, KOGMAW)), 10);

        // then
        assertThat(itemIdsOf(result)).contains(INCENSE, MOONSTONE);
    }

    @Test
    @DisplayName("같은 챔피언이라도 아군이 누구냐에 따라 다른 아이템이 뜬다 — 이 generator의 존재 이유")
    void generate_WhenAllyDiffers_SurfacesDifferentItems() {
        // when
        List<Long> withJinx = itemIdsOf(generator.generate(queryWithAllies(List.of(JINX)), 10));
        List<Long> withKogmaw = itemIdsOf(generator.generate(queryWithAllies(List.of(KOGMAW)), 10));

        // then
        assertThat(withJinx).contains(INCENSE).doesNotContain(MOONSTONE);
        assertThat(withKogmaw).contains(MOONSTONE);
    }

    @Test
    @DisplayName("아군별 점수를 개별 보존한다 — 향로는 징크스 쪽 점수가 코그모 쪽보다 높다")
    void retrieve_WhenScoringPerAlly_KeepsEachAllyScoreSeparately() {
        // when
        Map<Long, AllyScoreAggregate> byItem =
                retriever.scoresByItem(JANNA, List.of(JINX, KOGMAW), PairRelation.ALLY);

        // then: 향로는 징크스와 8/10, 코그모와 1/10
        AllyScoreAggregate incense = byItem.get(INCENSE);
        assertThat(incense.scoreOf(JINX)).isGreaterThan(incense.scoreOf(KOGMAW));
    }

    @Test
    @DisplayName("아군을 추가해도 기존 아군과의 점수는 변하지 않는다 — 5명을 하나의 window로 묶지 않는다는 증거")
    void retrieve_WhenAnotherAllyAdded_DoesNotChangeExistingAllyScores() {
        // given
        double beforeAdding = retriever.scoresByItem(JANNA, List.of(JINX), PairRelation.ALLY)
                .get(INCENSE).scoreOf(JINX);

        // when: 관계없는 아군을 하나 더 넣는다
        double afterAdding = retriever.scoresByItem(JANNA, List.of(JINX, KOGMAW, ORNN), PairRelation.ALLY)
                .get(INCENSE).scoreOf(JINX);

        // then: 통짜 window라면 아군이 늘 때마다 점수가 흔들린다
        assertThat(afterAdding).isEqualTo(beforeAdding);
    }

    @Test
    @DisplayName("아군 순서를 바꿔도 결과가 동일하다")
    void generate_WhenAllyOrderChanges_ProducesIdenticalResult() {
        // when
        GeneratorResult forward = generator.generate(queryWithAllies(List.of(JINX, KOGMAW, ORNN)), 10);
        GeneratorResult reversed = generator.generate(queryWithAllies(List.of(ORNN, KOGMAW, JINX)), 10);

        // then
        assertThat(itemIdsOf(reversed)).isEqualTo(itemIdsOf(forward));
    }

    @Test
    @DisplayName("함께한 판이 너무 적은 아군은 건너뛴다 — 우연을 궁합으로 읽지 않는다")
    void generate_WhenPairSampleIsTooThin_SkipsThatAlly() {
        // given: 잔나와 오른은 1판뿐이고 그때 솔라리를 샀다
        GeneratorResult result = generator.generate(queryWithAllies(List.of(JINX, ORNN)), 10);

        // then
        assertThat(itemIdsOf(result)).doesNotContain(LOCKET);
    }

    @Test
    @DisplayName("삼중항 표본이 충분하면 백오프하지 않는다")
    void generate_WhenTripleHasEnoughSupport_UsesTripleLevel() {
        // when
        GeneratorResult result = generator.generate(queryWithAllies(List.of(JINX, KOGMAW)), 10);

        // then
        assertThat(result.backoffLevel()).isEqualTo(PairBackoffLevel.TRIPLE.ordinal());
    }

    @Test
    @DisplayName("모든 아군의 표본이 부족하면 챔피언 base rate로 백오프한다 — 후보가 0이 되지 않게")
    void generate_WhenEveryAllyIsTooThin_BacksOffToChampionBaseRate() {
        // given: 아리와는 함께한 적이 없다
        GeneratorResult result = generator.generate(queryWithAllies(List.of(AHRI)), 10);

        // then
        assertThat(result.backoffLevel()).isEqualTo(PairBackoffLevel.BASE_RATE.ordinal());
        assertThat(itemIdsOf(result)).isNotEmpty();
    }

    @Test
    @DisplayName("topK를 넘는 후보는 내지 않는다")
    void generate_WhenMoreCandidatesThanTopK_LimitsResultSize() {
        assertThat(generator.generate(queryWithAllies(List.of(JINX, KOGMAW)), 1).rankedItems())
                .hasSizeLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("source는 ALLY_SYNERGY다")
    void source_IsAllySynergy() {
        assertThat(generator.source()).isEqualTo(CandidateSource.ALLY_SYNERGY);
    }
}
