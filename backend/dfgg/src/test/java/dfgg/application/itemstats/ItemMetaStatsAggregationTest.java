package dfgg.application.itemstats;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.itemstats.ItemMetaStats;
import dfgg.domain.itemstats.ItemMetaStatsRepository;
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
@Sql("/sql/item-stats-aggregation-test-data.sql")
class ItemMetaStatsAggregationTest {

    private static final long KRAKEN_SLAYER = 6673L;
    private static final long LOCKET = 3190L;
    private static final long WARMOG = 3071L;

    private static final int WINDOW_SIZE = 1;

    @Autowired
    private ItemStatsAggregationService aggregationService;

    @Autowired
    private ItemMetaStatsRepository itemMetaStatsRepository;

    @BeforeEach
    void aggregate() {
        aggregationService.aggregate(WINDOW_SIZE);
    }

    private ItemMetaStats metaOf(String patch, ChampionPosition position, long itemId) {
        return itemMetaStatsRepository.findByPatchAndPositionAndItemId(patch, position, itemId).orElseThrow();
    }

    @Test
    @DisplayName("패치를 키로 분리해 집계한다 — 여기만 patch가 키다")
    void aggregate_WhenSameItemAppearsInDifferentPatches_KeepsThemAsSeparateRows() {
        // when: 크라켄은 MID에서 16.15에 한 번, 16.17에 한 번 팔렸다
        ItemMetaStats older = metaOf("16.15", ChampionPosition.MID, KRAKEN_SLAYER);
        ItemMetaStats newer = metaOf("16.17", ChampionPosition.MID, KRAKEN_SLAYER);

        // then
        assertThat(older.getPickCount()).isEqualTo(1);
        assertThat(newer.getPickCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("scope_game_count는 그 패치·포지션의 참가자 수다 — 픽률의 분모")
    void aggregate_WhenStatsStored_IncludesScopeGameCountAsDenominator() {
        // when: 16.17 MID 참가자는 m2-a(157), m2-d(103), m3-b(33) 셋
        ItemMetaStats stats = metaOf("16.17", ChampionPosition.MID, KRAKEN_SLAYER);

        // then
        assertThat(stats.getScopeGameCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("패치별 승리 횟수를 집계한다")
    void aggregate_WhenWinsDifferAcrossPatches_CountsWinsPerPatch() {
        // when: 16.15 MID 크라켄은 승(M1), 16.17 MID 크라켄은 패(M2)
        assertThat(metaOf("16.15", ChampionPosition.MID, KRAKEN_SLAYER).getWinCount()).isEqualTo(1);
        assertThat(metaOf("16.17", ChampionPosition.MID, KRAKEN_SLAYER).getWinCount()).isZero();
    }

    @Test
    @DisplayName("패치 delta를 계산할 수 있게 아이템·포지션의 패치 계열을 조회한다")
    void findByPositionAndItemId_WhenQueried_ReturnsSeriesAcrossPatchesForDelta() {
        // when
        List<ItemMetaStats> series =
                itemMetaStatsRepository.findByPositionAndItemId(ChampionPosition.MID, KRAKEN_SLAYER);

        // then: 두 패치의 픽률을 비교할 수 있어야 버프/너프가 feature로 드러난다
        assertThat(series).hasSize(2);
        assertThat(series).extracting(ItemMetaStats::getPatch)
                .containsExactlyInAnyOrder("16.15", "16.17");
    }

    @Test
    @DisplayName("Riot 원시 포지션을 정규화해 저장한다 — UTILITY는 SUPPORT로")
    void aggregate_WhenRiotRawPositionGiven_NormalizesToChampionPosition() {
        // when & then
        assertThat(itemMetaStatsRepository
                .findByPatchAndPositionAndItemId("16.15", ChampionPosition.SUPPORT, LOCKET))
                .isPresent();
    }

    @Test
    @DisplayName("구매 순서가 불완전한 참가자는 집계에서 제외한다")
    void aggregate_WhenPurchaseOrderIncomplete_ExcludesThatParticipant() {
        // when & then: 999가 산 워모그는 어디에도 없어야 한다
        assertThat(itemMetaStatsRepository
                .findByPositionAndItemId(ChampionPosition.JUNGLE, WARMOG)).isEmpty();
    }

    @Test
    @DisplayName("두 번 실행해도 결과가 같다")
    void aggregate_WhenRunTwice_IsIdempotent() {
        // given
        long countAfterFirstRun = itemMetaStatsRepository.count();

        // when
        aggregationService.aggregate(WINDOW_SIZE);

        // then
        assertThat(itemMetaStatsRepository.count()).isEqualTo(countAfterFirstRun);
        assertThat(metaOf("16.17", ChampionPosition.MID, KRAKEN_SLAYER).getScopeGameCount()).isEqualTo(3);
    }
}
