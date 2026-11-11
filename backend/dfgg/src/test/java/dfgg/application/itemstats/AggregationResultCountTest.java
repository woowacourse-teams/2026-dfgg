package dfgg.application.itemstats;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.itemstats.ChampionItemRollupRepository;
import dfgg.domain.itemstats.ChampionItemStatsRepository;
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
class AggregationResultCountTest {

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

    @Test
    @DisplayName("결과의 행 수는 각 통계 테이블에 실제로 들어간 행 수와 같다")
    void aggregate_WhenCompleted_ReportsInsertedRowCounts() {
        // when
        ItemStatsAggregationResult result = serviceWithBuckets(3).aggregate(WINDOW_SIZE);

        // then
        assertThat(result.championPairItemStatsCount()).isPositive();
        assertThat(result.championItemStatsCount()).isEqualTo(championItemStatsRepository.count());
        assertThat(result.championItemRollupCount()).isEqualTo(championItemRollupRepository.count());
        assertThat(result.championPairItemStatsCount()).isEqualTo(championPairItemStatsRepository.count());
        assertThat(result.itemMetaStatsCount()).isEqualTo(itemMetaStatsRepository.count());
    }
}
