package dfgg.domain.itemstats;

import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChampionItemRollupRepository extends JpaRepository<ChampionItemRollup, Long> {

    Optional<ChampionItemRollup> findByChampionIdAndItemId(Integer championId, Long itemId);

    java.util.List<ChampionItemRollup> findByChampionId(Integer championId);

    /**
     * {@link ChampionItemStatsRepository#aggregateFrom}과 같은 집계를 포지션 없이 수행한다.
     * 포지션별 통계를 합산하는 게 아니라 원본에서 다시 센다 — 합산으로 만들면 게임 수 분모가
     * 포지션 수만큼 부풀려진다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO champion_item_rollup (
                champion_id, item_id,
                purchase_count_all, purchase_count_recent,
                win_count_all, win_count_recent,
                champion_game_count_all, champion_game_count_recent
            )
            WITH participant AS (
                SELECT champion_id, patch, win, core_item_purchase_order
                FROM normalized_match_participants
                WHERE core_item_purchase_order_complete
                  AND core_item_purchase_order <> ''
            ),
            champion_games AS (
                SELECT champion_id,
                       count(*) AS game_all,
                       count(*) FILTER (WHERE patch IN (:recentPatches)) AS game_recent
                FROM participant
                GROUP BY champion_id
            ),
            purchase AS (
                SELECT champion_id, patch, win,
                       unnest(string_to_array(core_item_purchase_order, ','))::bigint AS item_id
                FROM participant
            )
            SELECT purchase.champion_id, purchase.item_id,
                   count(*),
                   count(*) FILTER (WHERE purchase.patch IN (:recentPatches)),
                   count(*) FILTER (WHERE purchase.win),
                   count(*) FILTER (WHERE purchase.win AND purchase.patch IN (:recentPatches)),
                   max(champion_games.game_all),
                   max(champion_games.game_recent)
            FROM purchase
            JOIN champion_games ON champion_games.champion_id = purchase.champion_id
            GROUP BY purchase.champion_id, purchase.item_id
            """, nativeQuery = true)
    void aggregateFrom(@Param("recentPatches") Collection<String> recentPatches);
}
