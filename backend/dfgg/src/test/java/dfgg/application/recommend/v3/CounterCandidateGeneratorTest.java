package dfgg.application.recommend.v3;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.itemstats.ItemStatsAggregationService;
import dfgg.application.recommend.v3.generator.CounterCandidateGenerator;
import dfgg.application.recommend.v3.generator.CounterLift;
import dfgg.application.recommend.v3.generator.CounterLiftCalculator;
import dfgg.application.recommend.v3.generator.PairBackoffLevel;
import dfgg.application.utils.WilsonScoreCalculator;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.itemstats.ChampionItemRollupRepository;
import dfgg.domain.itemstats.ChampionItemStatsRepository;
import dfgg.domain.itemstats.ChampionPairItemStatsRepository;
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
@Sql("/sql/counter-test-data.sql")
class CounterCandidateGeneratorTest {

    private static final long YASUO = 157L;
    private static final long AHRI = 103L;
    private static final long RAMMUS = 33L;

    private static final long DOMINIK = 3036L;   // 야스오가 람머스 상대로 더 사는 것
    private static final long LIANDRY = 6653L;   // 아리가 산 것 — 야스오는 안 산다
    private static final long INFINITY_EDGE = 3031L;

    @Autowired
    private ItemStatsAggregationService aggregationService;
    @Autowired
    private ChampionPairItemStatsRepository pairRepository;
    @Autowired
    private ChampionItemStatsRepository championItemStatsRepository;
    @Autowired
    private ChampionItemRollupRepository championItemRollupRepository;

    private CounterCandidateGenerator generator;

    @BeforeEach
    void setUp() {
        aggregationService.aggregate(1);
        generator = new CounterCandidateGenerator(
                pairRepository, championItemStatsRepository, championItemRollupRepository,
                new CounterLiftCalculator(1.0, 159), new WilsonScoreCalculator(), 5
        );
    }

    private RecommendationQuery queryAgainst(List<Long> enemyChampionIds) {
        return new RecommendationQuery(
                YASUO, ChampionPosition.MID, List.of(),
                List.of(222L, 412L, 64L, 516L), enemyChampionIds,
                "EMERALD", "16.17"
        );
    }

    private List<Long> itemIdsOf(GeneratorResult result) {
        return result.rankedItems().stream().map(ScoredItem::itemId).toList();
    }

    @Test
    @DisplayName("아군이 산 아이템이 내 counter 근거로 넘어오지 않는다 — 이번 작업이 고치려는 결함")
    void generate_WhenTeammateBoughtItemAgainstThisEnemy_DoesNotAttributeItToMe() {
        // given: 아리는 람머스를 만나면 20판 내내 리안드리를 샀지만 야스오는 한 번도 안 샀다.
        GeneratorResult result = generator.generate(queryAgainst(List.of(RAMMUS)), 10);

        // then
        assertThat(itemIdsOf(result)).doesNotContain(LIANDRY);
    }

    @Test
    @DisplayName("내가 이 적 상대로 실제로 더 사는 아이템은 lift가 1보다 크다")
    void generate_WhenIBuyMoreAgainstThisEnemy_LiftExceedsOne() {
        // given: 야스오의 도미닉 구매율은 평소 45%(18/40)인데 람머스 상대로는 80%(16/20)
        Map<Long, CounterLift> lifts = generator.liftsByItem(YASUO, ChampionPosition.MID, RAMMUS);

        // then
        assertThat(lifts.get(DOMINIK).lift()).isGreaterThan(1.0);
    }

    @Test
    @DisplayName("내가 이 적 상대로 덜 사는 아이템은 lift가 1보다 작다")
    void generate_WhenIBuyLessAgainstThisEnemy_LiftIsBelowOne() {
        // given: 무한의 대검은 평소 55%(22/40)인데 람머스 상대로는 20%(4/20)
        Map<Long, CounterLift> lifts = generator.liftsByItem(YASUO, ChampionPosition.MID, RAMMUS);

        // then
        assertThat(lifts.get(INFINITY_EDGE).lift()).isLessThan(1.0);
    }

    @Test
    @DisplayName("lift와 함께 내 챔피언의 base rate를 별도로 남긴다 — 셋을 구분해야 실패 유형을 잡는다")
    void liftsByItem_WhenComputed_PreservesBaseRateSeparately() {
        // when
        Map<Long, CounterLift> lifts = generator.liftsByItem(YASUO, ChampionPosition.MID, RAMMUS);

        // then: 야스오의 도미닉 base rate는 18/40 = 0.45
        CounterLift dominik = lifts.get(DOMINIK);
        assertThat(dominik.baseRate()).isEqualTo(0.45);
        assertThat(dominik.pairProbability()).isEqualTo(16.0 / 20.0);
    }

    @Test
    @DisplayName("적 각각에 대해 따로 조회하고 결과를 union한다")
    void generate_WhenMultipleEnemies_UnionsPerEnemyCandidates() {
        // when
        GeneratorResult result = generator.generate(queryAgainst(List.of(RAMMUS, AHRI)), 10);

        // then: 람머스 상대로 도미닉과 아리 상대로 무한의 대검이 함께 나온다
        assertThat(itemIdsOf(result)).contains(DOMINIK, INFINITY_EDGE);
    }

    @Test
    @DisplayName("어느 적 때문에 올라온 후보인지를 결과에 남긴다 — 집계하면서 버리지 않는다")
    void generate_PreservesWhichEnemyDroveEachCandidate() {
        // 랭킹 점수로는 적별 lift의 최댓값 하나만 쓰지만, "누구 때문인가"는 추천 이유를
        // 만들 때 필요하다. 여기서 버리면 나중에 같은 통계를 다시 조회해야 한다.
        GeneratorResult result = generator.generate(queryAgainst(List.of(RAMMUS)), 10);

        ScoredItem dominik = result.rankedItems().stream()
                .filter(item -> item.itemId() == DOMINIK)
                .findFirst().orElseThrow();

        assertThat(dominik.scoreByChampionId()).containsKey(RAMMUS);
    }

    @Test
    @DisplayName("적이 여럿이면 각각의 lift를 따로 남긴다")
    void generate_WhenMultipleEnemies_KeepsEachEnemysLiftSeparately() {
        GeneratorResult result = generator.generate(queryAgainst(List.of(RAMMUS, AHRI)), 10);

        ScoredItem infinityEdge = result.rankedItems().stream()
                .filter(item -> item.itemId() == INFINITY_EDGE)
                .findFirst().orElseThrow();

        assertThat(infinityEdge.scoreByChampionId()).containsKeys(RAMMUS, AHRI);
    }

    @Test
    @DisplayName("남긴 적별 lift가 개별 조회 결과와 같다 — 다시 계산하면 값이 갈릴 수 있다")
    void generate_PreservedLiftMatchesTheDirectLookup() {
        double direct = generator.liftsByItem(YASUO, ChampionPosition.MID, RAMMUS).get(DOMINIK).lift();

        GeneratorResult result = generator.generate(queryAgainst(List.of(RAMMUS)), 10);
        ScoredItem dominik = result.rankedItems().stream()
                .filter(item -> item.itemId() == DOMINIK)
                .findFirst().orElseThrow();

        assertThat(dominik.scoreByChampionId().get(RAMMUS)).isEqualTo(direct);
    }

    @Test
    @DisplayName("랭킹 점수는 적별 lift의 최댓값 그대로다 — 근거를 남겨도 순위는 달라지지 않는다")
    void generate_RankingScoreStillEqualsTheMaximumPerEnemyLift() {
        GeneratorResult result = generator.generate(queryAgainst(List.of(RAMMUS, AHRI)), 10);

        assertThat(result.rankedItems()).allSatisfy(item ->
                assertThat(item.score())
                        .isEqualTo(item.scoreByChampionId().values().stream()
                                .mapToDouble(Double::doubleValue).max().orElseThrow()));
    }

    @Test
    @DisplayName("적을 추가해도 기존 적과의 lift는 변하지 않는다 — 적 5명을 하나의 window로 묶지 않는다")
    void liftsByItem_WhenAnotherEnemyAdded_DoesNotChangeExistingEnemyLift() {
        // given
        double aloneAgainstRammus = generator.liftsByItem(YASUO, ChampionPosition.MID, RAMMUS).get(DOMINIK).lift();

        // when
        GeneratorResult withBothEnemies = generator.generate(queryAgainst(List.of(RAMMUS, AHRI)), 10);
        double stillAgainstRammus =
                generator.liftsByItem(YASUO, ChampionPosition.MID, RAMMUS).get(DOMINIK).lift();

        // then
        assertThat(stillAgainstRammus).isEqualTo(aloneAgainstRammus);
        assertThat(withBothEnemies.rankedItems()).isNotEmpty();
    }

    @Test
    @DisplayName("적 순서를 바꿔도 결과가 동일하다")
    void generate_WhenEnemyOrderChanges_ProducesIdenticalResult() {
        // when
        GeneratorResult forward = generator.generate(queryAgainst(List.of(RAMMUS, AHRI)), 10);
        GeneratorResult reversed = generator.generate(queryAgainst(List.of(AHRI, RAMMUS)), 10);

        // then
        assertThat(itemIdsOf(reversed)).isEqualTo(itemIdsOf(forward));
    }

    @Test
    @DisplayName("삼중항 표본이 충분하면 백오프하지 않는다")
    void generate_WhenTripleHasEnoughSupport_UsesTripleLevel() {
        assertThat(generator.generate(queryAgainst(List.of(RAMMUS)), 10).backoffLevel())
                .isEqualTo(PairBackoffLevel.TRIPLE.ordinal());
    }

    @Test
    @DisplayName("만난 적 없는 적뿐이면 챔피언 base rate로 백오프한다")
    void generate_WhenNoEnemyObserved_BacksOffToChampionBaseRate() {
        // given: 99999는 만난 적이 없다
        GeneratorResult result = generator.generate(queryAgainst(List.of(99999L)), 10);

        // then
        assertThat(result.backoffLevel()).isEqualTo(PairBackoffLevel.BASE_RATE.ordinal());
        assertThat(result.rankedItems()).isNotEmpty();
    }

    @Test
    @DisplayName("topK를 넘는 후보는 내지 않는다")
    void generate_WhenMoreCandidatesThanTopK_LimitsResultSize() {
        assertThat(generator.generate(queryAgainst(List.of(RAMMUS, AHRI)), 1).rankedItems())
                .hasSizeLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("source는 COUNTER다")
    void source_IsCounter() {
        assertThat(generator.source()).isEqualTo(CandidateSource.COUNTER);
    }
}
