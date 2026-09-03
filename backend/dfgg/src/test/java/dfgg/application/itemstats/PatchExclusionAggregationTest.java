package dfgg.application.itemstats;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.itemstats.ChampionItemStats;
import dfgg.domain.itemstats.ChampionItemStatsRepository;
import dfgg.domain.itemstats.ItemMetaStats;
import dfgg.domain.itemstats.ItemMetaStatsRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

/**
 * 특정 패치를 통계에서 제외하고 집계한다.
 * <p>
 * patch split 평가에서 test 패치의 경기가 통계에 섞여 있으면, 모델이 "아직 오지 않은 패치"를 이미 본 셈이 된다.
 * 평가가 정직하려면 집계 단계에서 잘라야 한다.
 * <p>
 * 픽스처: 야스오 MID는 16.15에 1경기(승, 크라켄+무한의 대검), 16.17에 1경기(패, 크라켄).
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ItemStatsAggregationService.class)
@Sql("/sql/item-stats-aggregation-test-data.sql")
class PatchExclusionAggregationTest {

    private static final int YASUO = 157;
    private static final long KRAKEN_SLAYER = 6673L;
    private static final int WINDOW_SIZE = 1;

    @Autowired
    private ItemStatsAggregationService aggregationService;

    @Autowired
    private ChampionItemStatsRepository championItemStatsRepository;

    @Autowired
    private ItemMetaStatsRepository itemMetaStatsRepository;

    private ChampionItemStats yasuoKraken() {
        return championItemStatsRepository.findAll().stream()
                .filter(stats -> stats.getChampionId() == YASUO
                        && stats.getPosition() == ChampionPosition.MID
                        && stats.getItemId() == KRAKEN_SLAYER)
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("제외한 패치의 경기는 구매 수에 들어가지 않는다")
    void aggregate_WhenPatchIsExcluded_DropsItsPurchases() {
        aggregationService.aggregate(WINDOW_SIZE, Set.of("16.17"));

        // 16.15의 1건만 남는다. 제외하지 않으면 2가 된다.
        assertThat(yasuoKraken().getPurchaseCountAll()).isEqualTo(1);
    }

    @Test
    @DisplayName("제외한 패치의 경기는 챔피언 게임 수(분모)에서도 빠진다")
    void aggregate_WhenPatchIsExcluded_DropsItFromTheDenominator() {
        aggregationService.aggregate(WINDOW_SIZE, Set.of("16.17"));

        assertThat(yasuoKraken().getChampionGameCountAll()).isEqualTo(1);
    }

    @Test
    @DisplayName("최근 패치 윈도를 제외 후 남은 패치에서 고른다 — 제외한 패치가 '최근'이면 누수가 그대로다")
    void aggregate_WhenPatchIsExcluded_RecentWindowIgnoresIt() {
        ItemStatsAggregationResult result = aggregationService.aggregate(WINDOW_SIZE, Set.of("16.17"));

        assertThat(result.recentPatches()).containsExactly("16.15");
    }

    @Test
    @DisplayName("아무것도 제외하지 않으면 기존 집계와 같다 — 서빙 경로의 동작은 그대로여야 한다")
    void aggregate_WhenNothingIsExcluded_MatchesTheUnfilteredAggregation() {
        aggregationService.aggregate(WINDOW_SIZE, Set.of());
        long withEmptySet = yasuoKraken().getPurchaseCountAll();

        aggregationService.aggregate(WINDOW_SIZE);

        assertThat(yasuoKraken().getPurchaseCountAll()).isEqualTo(withEmptySet).isEqualTo(2);
    }

    @Test
    @DisplayName("패치별로 키가 잡힌 item_meta_stats에도 제외한 패치의 행이 남지 않는다")
    void aggregate_WhenPatchIsExcluded_LeavesNoMetaStatsRowForIt() {
        // 다른 통계 테이블은 patch를 키에 두지 않아 제외 여부가 값으로만 드러나지만,
        // item_meta_stats는 patch로 키가 잡혀 있어 누수를 행의 유무로 직접 확인할 수 있다.
        aggregationService.aggregate(WINDOW_SIZE, Set.of("16.17"));

        assertThat(itemMetaStatsRepository.findAll())
                .isNotEmpty()
                .extracting(ItemMetaStats::getPatch)
                .doesNotContain("16.17")
                .contains("16.15");
    }

    @Test
    @DisplayName("모든 패치를 제외하면 통계가 비어 조용히 0을 쓰는 대신 결과가 0행이 된다")
    void aggregate_WhenEveryPatchIsExcluded_ProducesNoStats() {
        aggregationService.aggregate(WINDOW_SIZE, Set.of("16.15", "16.17"));

        assertThat(championItemStatsRepository.count()).isZero();
    }
}
