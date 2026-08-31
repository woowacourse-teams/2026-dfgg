package dfgg.domain.itemstats;

import dfgg.domain.champion.ChampionPosition;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ItemMetaStatsRepository extends JpaRepository<ItemMetaStats, Long> {

    Optional<ItemMetaStats> findByPatchAndPositionAndItemId(
            String patch, ChampionPosition position, Long itemId
    );

    /** 패치 delta feature의 원천 — 한 아이템의 패치별 계열. 정렬은 호출자가 PatchVersion으로 한다. */
    List<ItemMetaStats> findByPositionAndItemId(ChampionPosition position, Long itemId);

    List<ItemMetaStats> findByPatchAndPosition(String patch, ChampionPosition position);

    /**
     * 패치·포지션별 아이템 픽률 통계를 원본에서 다시 만든다.
     * 여기서만 patch가 GROUP BY 키에 들어간다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO item_meta_stats (patch, position, item_id, pick_count, win_count, scope_game_count)
            WITH participant AS (
                SELECT patch,
                       CASE position
                           WHEN 'MIDDLE' THEN 'MID'
                           WHEN 'UTILITY' THEN 'SUPPORT'
                           ELSE position
                       END AS normalized_position,
                       win, core_item_purchase_order
                FROM normalized_match_participants
                WHERE core_item_purchase_order_complete
                  AND core_item_purchase_order <> ''
                  AND position IN ('TOP', 'JUNGLE', 'MID', 'MIDDLE', 'BOTTOM', 'SUPPORT', 'UTILITY')
            ),
            scope AS (
                SELECT patch, normalized_position, count(*) AS scope_game_count
                FROM participant
                GROUP BY patch, normalized_position
            ),
            pick AS (
                SELECT patch, normalized_position, win,
                       unnest(string_to_array(core_item_purchase_order, ','))::bigint AS item_id
                FROM participant
            )
            SELECT pick.patch, pick.normalized_position, pick.item_id,
                   count(*),
                   count(*) FILTER (WHERE pick.win),
                   max(scope.scope_game_count)
            FROM pick
            JOIN scope
              ON scope.patch = pick.patch
             AND scope.normalized_position = pick.normalized_position
            GROUP BY pick.patch, pick.normalized_position, pick.item_id
            """, nativeQuery = true)
    void aggregateFrom();
}
