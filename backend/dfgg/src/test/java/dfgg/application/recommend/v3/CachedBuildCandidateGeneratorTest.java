package dfgg.application.recommend.v3;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.ChampionPositionNormalizer;
import dfgg.application.itemstats.ItemStatsAggregationService;
import dfgg.application.recommend.v3.generator.BuildCandidateGenerator;
import dfgg.application.recommend.v3.generator.RecentPatchWindowCache;
import dfgg.application.recommend.v3.generator.transition.CachingBuildTransitionReader;
import dfgg.application.recommend.v3.generator.transition.DatabaseBuildTransitionReader;
import dfgg.application.utils.WilsonScoreCalculator;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.itemstats.ChampionItemStatsRepository;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.infrastructure.config.TierScopeConfiguration;
import java.util.List;
import java.util.Set;
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
@Import({TierScopeConfiguration.class, ItemStatsAggregationService.class})
@Sql("/sql/build-generator-test-data.sql")
class CachedBuildCandidateGeneratorTest {

    private static final Set<Long> CORE_ITEM_IDS = Set.of(3006L, 3031L, 3033L, 3036L, 3072L, 3153L, 6673L);
    private static final int WINDOW_SIZE = 1;

    @Autowired
    private NormalizedMatchParticipantRepository participantRepository;

    @Autowired
    private ChampionItemStatsRepository championItemStatsRepository;

    @Autowired
    private ItemStatsAggregationService aggregationService;

    private BuildCandidateGenerator databaseOnly;
    private BuildCandidateGenerator cached;

    @BeforeEach
    void setUp() {
        aggregationService.aggregate(WINDOW_SIZE);
        RecentPatchWindowCache window = new RecentPatchWindowCache(participantRepository, WINDOW_SIZE);
        DatabaseBuildTransitionReader database = new DatabaseBuildTransitionReader(
                participantRepository, new ChampionPositionNormalizer(), window);
        databaseOnly = new BuildCandidateGenerator(database, championItemStatsRepository, new WilsonScoreCalculator());
        cached = new BuildCandidateGenerator(
                CachingBuildTransitionReader.of(database, window, 100),
                championItemStatsRepository, new WilsonScoreCalculator());
    }

    private RecommendationQuery queryWith(List<Long> purchasedItemIds) {
        return new RecommendationQuery(
                157L, ChampionPosition.MID, purchasedItemIds,
                List.of(222L, 412L, 64L, 516L), List.of(33L, 103L, 51L, 89L, 60L),
                "EMERALD", "16.17", CORE_ITEM_IDS
        );
    }

    @Test
    @DisplayName("캐시를 거쳐도 처음 읽을 때와 다시 읽을 때 모두 DB만 읽은 결과와 같다")
    void generate_WhenCached_ReturnsSameResultAsDatabaseOnly() {
        // given: 정확한 prefix, 마지막 아이템, 챔피언 단위로 물러서는 경우를 모두 넣는다
        List<List<Long>> purchases = List.of(
                List.of(), List.of(6673L), List.of(6673L, 3031L), List.of(88888L, 6673L), List.of(88888L));

        for (List<Long> purchased : purchases) {
            // when
            GeneratorResult expected = databaseOnly.generate(queryWith(purchased), 10);
            GeneratorResult firstRead = cached.generate(queryWith(purchased), 10);
            GeneratorResult cachedRead = cached.generate(queryWith(purchased), 10);

            // then
            assertThat(firstRead).isEqualTo(expected);
            assertThat(cachedRead).isEqualTo(expected);
        }
    }
}
