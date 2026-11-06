package dfgg.application.itemstats;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.itemstats.ChampionItemRollup;
import dfgg.domain.itemstats.ChampionItemRollupRepository;
import dfgg.domain.itemstats.ChampionItemStats;
import dfgg.domain.itemstats.ChampionItemStatsRepository;
import dfgg.domain.itemstats.ChampionPairItemStats;
import dfgg.domain.itemstats.ChampionPairItemStatsRepository;
import dfgg.domain.itemstats.ItemMetaStats;
import dfgg.domain.itemstats.ItemMetaStatsRepository;
import dfgg.domain.itemstats.PairRelation;
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

/**
 * 집계가 {@code recommendation.v3.tiers} 범위 밖 구매를 세지 않는지 확인한다.
 * <p>
 * 픽스처는 티어만 다르고 나머지가 같은 구매를 넣어 두었다 — 야스오 MID가 EMERALD에서 2판, PLATINUM에서 3판 같은 아이템을 산다.
 * 범위를 바꿔가며 같은 픽스처를 집계해 결과가 범위를 따라 움직이는지를 본다.
 * 설정을 읽고도 어딘가에서 무시하는 배선 실수는 값이 하나뿐이면 드러나지 않는다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("/sql/tier-scoped-aggregation-test-data.sql")
class TierScopedAggregationTest {

    private static final int YASUO = 157;
    private static final int THRESH = 412;
    private static final long IMMORTAL_SHIELDBOW = 6673L;
    private static final int WINDOW_SIZE = 1;

    @Autowired private NormalizedMatchParticipantRepository participantRepository;
    @Autowired private ChampionItemStatsRepository championItemStatsRepository;
    @Autowired private ChampionItemRollupRepository championItemRollupRepository;
    @Autowired private ChampionPairItemStatsRepository championPairItemStatsRepository;
    @Autowired private ItemMetaStatsRepository itemMetaStatsRepository;

    private ItemStatsAggregationService aggregationServiceFor(String... tiers) {
        return new ItemStatsAggregationService(
                participantRepository, championItemStatsRepository, championItemRollupRepository,
                championPairItemStatsRepository, itemMetaStatsRepository,
                TierScope.of(List.of(tiers), "test"));
    }

    @Test
    @DisplayName("범위 밖 티어의 구매는 champion_item_stats에 잡히지 않는다")
    void aggregate_WhenTierOutOfScope_ExcludeItsPurchases() {
        // when
        aggregationServiceFor("EMERALD").aggregate(WINDOW_SIZE);

        // then
        assertThat(purchaseCount()).as("EMERALD 2판만 세어야 한다").isEqualTo(2);
    }

    @Test
    @DisplayName("범위를 넓히면 그만큼 더 센다 — 설정이 실제로 반영된다")
    void aggregate_WhenScopeWidened_CountMorePurchases() {
        // when
        aggregationServiceFor("EMERALD", "PLATINUM").aggregate(WINDOW_SIZE);

        // then
        assertThat(purchaseCount()).as("EMERALD 2판 + PLATINUM 3판").isEqualTo(5);
    }

    @Test
    @DisplayName("champion_item_rollup도 같은 범위를 따른다")
    void aggregate_WhenTierOutOfScope_RollupFollowsTheSameScope() {
        // when
        aggregationServiceFor("EMERALD").aggregate(WINDOW_SIZE);

        // then
        assertThat(championItemRollupRepository.findByChampionId(YASUO).stream()
                .filter(stat -> stat.getItemId().equals(IMMORTAL_SHIELDBOW))
                .mapToInt(ChampionItemRollup::getPurchaseCountAll)
                .sum()).isEqualTo(2);
    }

    @Test
    @DisplayName("item_meta_stats도 같은 범위를 따른다")
    void aggregate_WhenTierOutOfScope_MetaStatsFollowTheSameScope() {
        // when
        aggregationServiceFor("EMERALD").aggregate(WINDOW_SIZE);

        // then
        assertThat(itemMetaStatsRepository.findByPositionAndItemId(
                ChampionPosition.MID, IMMORTAL_SHIELDBOW).stream()
                .mapToInt(ItemMetaStats::getPickCount)
                .sum()).isEqualTo(2);
    }

    @Test
    @DisplayName("pair 통계에서 구매자는 범위로 거르되, 맥락이 되는 상대는 거르지 않는다")
    void aggregate_WhenTierOutOfScope_FilterPurchaserButKeepContext() {
        // when
        aggregationServiceFor("EMERALD").aggregate(WINDOW_SIZE);

        // then
        ChampionPairItemStats pair = championPairItemStatsRepository
                .findByMyChampionIdAndRelationAndOtherChampionIdIn(
                        YASUO, PairRelation.ALLY, List.of(THRESH)).stream()
                .filter(stat -> stat.getItemId().equals(IMMORTAL_SHIELDBOW))
                .findFirst()
                .orElseThrow();
        assertThat(pair.getCoCountAll()).as("EMERALD 구매만 센다").isEqualTo(2);
        assertThat(pair.getPairGameCountAll()).as("함께 등장한 판 수는 구매자 기준 2판").isEqualTo(2);
    }

    private int purchaseCount() {
        return championItemStatsRepository
                .findByChampionIdAndPosition(YASUO, ChampionPosition.MID).stream()
                .filter(stat -> stat.getItemId().equals(IMMORTAL_SHIELDBOW))
                .mapToInt(ChampionItemStats::getPurchaseCountAll)
                .sum();
    }
}
