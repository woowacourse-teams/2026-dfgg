package dfgg.application.itemstats;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.itemstats.ChampionPairItemStats;
import dfgg.domain.itemstats.ChampionPairItemStatsRepository;
import dfgg.domain.itemstats.PairRelation;
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
class ChampionPairItemStatsAggregationTest {

    private static final int YASUO = 157;
    private static final int JINX = 222;
    private static final int RAMMUS = 33;
    private static final int INCOMPLETE_CHAMPION = 999;
    private static final long KRAKEN_SLAYER = 6673L;
    private static final long INFINITY_EDGE = 3031L;

    private static final int WINDOW_SIZE = 1;

    @Autowired
    private ItemStatsAggregationService aggregationService;

    @Autowired
    private ChampionPairItemStatsRepository pairRepository;

    @BeforeEach
    void aggregate() {
        aggregationService.aggregate(WINDOW_SIZE);
    }

    private ChampionPairItemStats pairOf(int myChampionId, int otherChampionId, PairRelation relation, long itemId) {
        return pairRepository
                .findByMyChampionIdAndOtherChampionIdAndRelationAndItemId(
                        myChampionId, otherChampionId, relation, itemId)
                .orElseThrow();
    }

    @Test
    @DisplayName("같은 팀은 ALLY로 집계한다 — 야스오와 징크스는 M1·M2에서 아군")
    void aggregate_WhenChampionsOnSameTeam_RecordsAllyRelation() {
        // when
        ChampionPairItemStats ally = pairOf(YASUO, JINX, PairRelation.ALLY, KRAKEN_SLAYER);

        // then
        assertThat(ally.getCoCountAll()).isEqualTo(2);
    }

    @Test
    @DisplayName("다른 팀은 ENEMY로 집계한다 — 야스오와 람머스는 세 판 모두 적")
    void aggregate_WhenChampionsOnOpposingTeams_RecordsEnemyRelation() {
        // when
        ChampionPairItemStats enemy = pairOf(YASUO, RAMMUS, PairRelation.ENEMY, KRAKEN_SLAYER);

        // then
        assertThat(enemy.getCoCountAll()).isEqualTo(3);
        assertThat(enemy.getCoCountRecent()).isEqualTo(2);
    }

    @Test
    @DisplayName("아이템을 산 쪽이 누구인지 보존한다 — 야스오가 산 아이템만 야스오의 통계에 들어간다")
    void aggregate_WhenTeammateBoughtDifferentItem_DoesNotAttributeItToMe() {
        // given: 징크스는 M2에서 6672를 샀지만 야스오는 산 적이 없다
        // when & then
        assertThat(pairRepository.findByMyChampionIdAndOtherChampionIdAndRelationAndItemId(
                YASUO, JINX, PairRelation.ALLY, 6672L)).isEmpty();
    }

    @Test
    @DisplayName("pair_game_count는 아이템과 무관하게 그 조합이 함께 나온 판 수다 — 확률의 분모")
    void aggregate_WhenPairCoOccurred_StoresPairGameCountAsDenominator() {
        // when: 야스오·람머스는 M1·M2·M3 세 판, 그중 16.17은 두 판
        ChampionPairItemStats enemy = pairOf(YASUO, RAMMUS, PairRelation.ENEMY, KRAKEN_SLAYER);

        // then
        assertThat(enemy.getPairGameCountAll()).isEqualTo(3);
        assertThat(enemy.getPairGameCountRecent()).isEqualTo(2);
    }

    @Test
    @DisplayName("한 판에만 나온 아이템도 그 조합의 전체 판 수를 분모로 갖는다")
    void aggregate_WhenItemBoughtInOnlyOneGame_StillCarriesFullPairGameCount() {
        // when: 무한의 대검은 M1에서만 샀지만 야스오·람머스는 세 판을 함께 했다
        ChampionPairItemStats enemy = pairOf(YASUO, RAMMUS, PairRelation.ENEMY, INFINITY_EDGE);

        // then
        assertThat(enemy.getCoCountAll()).isEqualTo(1);
        assertThat(enemy.getPairGameCountAll()).isEqualTo(3);
    }

    @Test
    @DisplayName("맥락이 되는 상대는 구매 순서가 불완전해도 집계에 남는다 — 분모가 되는 건 조합의 등장이지 그의 아이템이 아니다")
    void aggregate_WhenContextChampionHasIncompleteOrder_StillCountsAsPairContext() {
        // when: 999는 구매 순서가 불완전하지만 M1에서 야스오의 아군이었다
        ChampionPairItemStats ally = pairOf(YASUO, INCOMPLETE_CHAMPION, PairRelation.ALLY, KRAKEN_SLAYER);

        // then
        assertThat(ally.getCoCountAll()).isEqualTo(1);
        assertThat(ally.getPairGameCountAll()).isEqualTo(1);
    }

    @Test
    @DisplayName("구매 순서가 불완전한 참가자 자신의 아이템은 집계하지 않는다")
    void aggregate_WhenMyOrderIsIncomplete_ProducesNoRowsForMe() {
        // when & then
        assertThat(pairRepository.findByMyChampionId(INCOMPLETE_CHAMPION)).isEmpty();
    }

    @Test
    @DisplayName("자기 자신과의 조합은 만들지 않는다")
    void aggregate_WhenAggregating_ExcludesSelfPairs() {
        // when & then
        assertThat(pairRepository.findByMyChampionIdAndOtherChampionId(YASUO, YASUO)).isEmpty();
    }

    @Test
    @DisplayName("승리 횟수를 전체·최근 각각 집계한다")
    void aggregate_WhenWinsAndLossesMixed_CountsWinsForBothScopes() {
        // when: 야스오·람머스 매치업에서 크라켄 — M1 승, M2 패, M3 승
        ChampionPairItemStats enemy = pairOf(YASUO, RAMMUS, PairRelation.ENEMY, KRAKEN_SLAYER);

        // then
        assertThat(enemy.getWinCountAll()).isEqualTo(2);
        assertThat(enemy.getWinCountRecent()).isEqualTo(1);
    }

    @Test
    @DisplayName("두 번 실행해도 결과가 같다")
    void aggregate_WhenRunTwice_IsIdempotent() {
        // given
        long countAfterFirstRun = pairRepository.count();

        // when
        aggregationService.aggregate(WINDOW_SIZE);

        // then
        assertThat(pairRepository.count()).isEqualTo(countAfterFirstRun);
        assertThat(pairOf(YASUO, RAMMUS, PairRelation.ENEMY, KRAKEN_SLAYER).getCoCountAll()).isEqualTo(3);
    }
}
