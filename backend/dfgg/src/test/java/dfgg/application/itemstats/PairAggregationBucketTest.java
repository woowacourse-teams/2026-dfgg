package dfgg.application.itemstats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.domain.itemstats.ChampionItemRollupRepository;
import dfgg.domain.itemstats.ChampionItemStatsRepository;
import dfgg.domain.itemstats.ChampionPairItemStats;
import dfgg.domain.itemstats.ChampionPairItemStatsRepository;
import dfgg.domain.itemstats.ItemMetaStatsRepository;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.match.TierScope;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("/sql/item-stats-aggregation-test-data.sql")
class PairAggregationBucketTest {

    private static final int WINDOW_SIZE = 1;

    @Autowired private NormalizedMatchParticipantRepository participantRepository;
    @Autowired private ChampionItemStatsRepository championItemStatsRepository;
    @Autowired private ChampionItemRollupRepository championItemRollupRepository;
    @Autowired private ChampionPairItemStatsRepository championPairItemStatsRepository;
    @Autowired private ItemMetaStatsRepository itemMetaStatsRepository;

    private ItemStatsAggregationService serviceWithBuckets(int buckets) {
        return new ItemStatsAggregationService(
                participantRepository, championItemStatsRepository, championItemRollupRepository,
                championPairItemStatsRepository, itemMetaStatsRepository,
                TierScope.of(List.of("EMERALD"), "test"), buckets);
    }

    private List<String> pairRows() {
        return championPairItemStatsRepository.findAll().stream()
                .map(this::describe)
                .sorted()
                .toList();
    }

    private String describe(ChampionPairItemStats stats) {
        return String.join("|",
                String.valueOf(stats.getMyChampionId()), String.valueOf(stats.getOtherChampionId()),
                stats.getRelation().name(), String.valueOf(stats.getItemId()),
                String.valueOf(stats.getCoCountAll()), String.valueOf(stats.getCoCountRecent()),
                String.valueOf(stats.getWinCountAll()), String.valueOf(stats.getWinCountRecent()),
                String.valueOf(stats.getPairGameCountAll()), String.valueOf(stats.getPairGameCountRecent()),
                String.valueOf(stats.getPairWinCountAll()));
    }

    @Test
    @DisplayName("챔피언 구간으로 나눠 집계해도 한 번에 집계한 결과와 같다")
    void aggregate_WhenSplitIntoBuckets_ProducesSameRows() {
        // given
        serviceWithBuckets(1).aggregate(WINDOW_SIZE);
        List<String> whole = pairRows();

        // when
        serviceWithBuckets(7).aggregate(WINDOW_SIZE);

        // then
        assertThat(whole).isNotEmpty();
        assertThat(pairRows()).isEqualTo(whole);
    }

    @Test
    @DisplayName("구간 수가 1보다 작으면 거부한다")
    void create_WhenBucketsLessThanOne_Throws() {
        assertThatThrownBy(() -> serviceWithBuckets(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
