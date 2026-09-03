package dfgg.application.itemstats;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.itemstats.ChampionItemRollup;
import dfgg.domain.itemstats.ChampionItemRollupRepository;
import dfgg.domain.itemstats.ChampionItemStats;
import dfgg.domain.itemstats.ChampionItemStatsRepository;
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
class ChampionItemStatsAggregationTest {

    private static final int YASUO = 157;
    private static final int THRESH = 412;
    private static final int INCOMPLETE_CHAMPION = 999;
    private static final long KRAKEN_SLAYER = 6673L;
    private static final long INFINITY_EDGE = 3031L;
    private static final long LOCKET = 3190L;

    /** 윈도 1 → recent = {16.17}. 픽스처에서 16.15는 과거, 16.17은 최근이 된다. */
    private static final int WINDOW_SIZE = 1;

    @Autowired
    private ItemStatsAggregationService aggregationService;

    @Autowired
    private ChampionItemStatsRepository championItemStatsRepository;

    @Autowired
    private ChampionItemRollupRepository championItemRollupRepository;

    @BeforeEach
    void aggregate() {
        aggregationService.aggregate(WINDOW_SIZE);
    }

    private ChampionItemStats statsOf(int championId, ChampionPosition position, long itemId) {
        return championItemStatsRepository
                .findByChampionIdAndPositionAndItemId(championId, position, itemId)
                .orElseThrow();
    }

    @Test
    @DisplayName("전체 구매 횟수를 집계한다 — 야스오 MID의 크라켄은 M1·M2 두 번")
    void aggregate_WhenChampionBoughtItemAcrossMatches_CountsAllPurchases() {
        // when
        ChampionItemStats stats = statsOf(YASUO, ChampionPosition.MID, KRAKEN_SLAYER);

        // then
        assertThat(stats.getPurchaseCountAll()).isEqualTo(2);
    }

    @Test
    @DisplayName("최근 윈도 구매 횟수를 따로 집계한다 — 16.17의 M2 한 번뿐")
    void aggregate_WhenSomePurchasesAreOutsideRecentWindow_CountsRecentSeparately() {
        // when
        ChampionItemStats stats = statsOf(YASUO, ChampionPosition.MID, KRAKEN_SLAYER);

        // then
        assertThat(stats.getPurchaseCountRecent()).isEqualTo(1);
    }

    @Test
    @DisplayName("승리 횟수를 전체·최근 각각 집계한다 — M1은 승, M2는 패")
    void aggregate_WhenWinsAndLossesMixed_CountsWinsForBothScopes() {
        // when
        ChampionItemStats stats = statsOf(YASUO, ChampionPosition.MID, KRAKEN_SLAYER);

        // then
        assertThat(stats.getWinCountAll()).isEqualTo(1);
        assertThat(stats.getWinCountRecent()).isZero();
    }

    @Test
    @DisplayName("챔피언 게임 수를 분모로 함께 저장한다 — P(item|champion) 계산의 분모")
    void aggregate_WhenStatsStored_IncludesChampionGameCountAsDenominator() {
        // when: 야스오는 MID로 M1·M2 두 판, 그중 16.17은 M2 한 판
        ChampionItemStats stats = statsOf(YASUO, ChampionPosition.MID, KRAKEN_SLAYER);

        // then
        assertThat(stats.getChampionGameCountAll()).isEqualTo(2);
        assertThat(stats.getChampionGameCountRecent()).isEqualTo(1);
    }

    @Test
    @DisplayName("최근 윈도 밖에서만 산 아이템은 recent 카운트가 0이다")
    void aggregate_WhenItemOnlyBoughtInOldPatch_HasZeroRecentCount() {
        // when: 무한의 대검은 M1(16.15)에서만 샀다
        ChampionItemStats stats = statsOf(YASUO, ChampionPosition.MID, INFINITY_EDGE);

        // then
        assertThat(stats.getPurchaseCountAll()).isEqualTo(1);
        assertThat(stats.getPurchaseCountRecent()).isZero();
    }

    @Test
    @DisplayName("Riot 원시 포지션을 정규화해 저장한다 — UTILITY는 SUPPORT로")
    void aggregate_WhenRiotRawPositionGiven_NormalizesToChampionPosition() {
        // when & then: 쓰레쉬는 UTILITY로 기록돼 있다
        assertThat(championItemStatsRepository
                .findByChampionIdAndPositionAndItemId(THRESH, ChampionPosition.SUPPORT, LOCKET))
                .isPresent();
    }

    @Test
    @DisplayName("구매 순서가 불완전한 참가자는 집계에서 제외한다")
    void aggregate_WhenPurchaseOrderIncomplete_ExcludesThatParticipant() {
        // when & then
        assertThat(championItemStatsRepository.findByChampionId(INCOMPLETE_CHAMPION)).isEmpty();
    }

    @Test
    @DisplayName("rollup은 포지션을 넘어 합산한다 — 야스오 크라켄은 MID 2번 + TOP 1번 = 3번")
    void aggregate_WhenChampionPlaysMultiplePositions_RollupSumsAcrossPositions() {
        // when
        ChampionItemRollup rollup = championItemRollupRepository
                .findByChampionIdAndItemId(YASUO, KRAKEN_SLAYER).orElseThrow();

        // then
        assertThat(rollup.getPurchaseCountAll()).isEqualTo(3);
        assertThat(rollup.getPurchaseCountRecent()).isEqualTo(2);
        assertThat(rollup.getChampionGameCountAll()).isEqualTo(3);
    }

    @Test
    @DisplayName("두 번 실행해도 결과가 같다 — 배치는 멱등이어야 재실행이 안전하다")
    void aggregate_WhenRunTwice_IsIdempotent() {
        // given: @BeforeEach 에서 이미 한 번 집계됨
        long countAfterFirstRun = championItemStatsRepository.count();

        // when
        aggregationService.aggregate(WINDOW_SIZE);

        // then
        assertThat(championItemStatsRepository.count()).isEqualTo(countAfterFirstRun);
        assertThat(statsOf(YASUO, ChampionPosition.MID, KRAKEN_SLAYER).getPurchaseCountAll()).isEqualTo(2);
    }
}
