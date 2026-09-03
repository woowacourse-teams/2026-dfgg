package dfgg.domain.itemstats;

import dfgg.domain.champion.ChampionPosition;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChampionItemStatsRepository extends JpaRepository<ChampionItemStats, Long> {

    Optional<ChampionItemStats> findByChampionIdAndPositionAndItemId(
            Integer championId, ChampionPosition position, Long itemId
    );

    List<ChampionItemStats> findByChampionId(Integer championId);

    List<ChampionItemStats> findByChampionIdAndPosition(Integer championId, ChampionPosition position);

    /**
     * {@code normalized_match_participants}에서 챔피언·포지션·아이템 통계를 통째로 다시 만든다.
     *
     * <p>참가자 861,080명을 자바로 끌어올려 집계하면 메모리와 시간이 모두 감당이 안 된다.
     * 집계는 DB가 하고 자바는 최근 윈도 판정({@code recentPatches})만 넘긴다 — 그 판정은
     * 문자열 정렬로는 틀리기 때문에({@code "16.10" < "16.9"}) 자바 쪽 버전 비교를 거친다.
     *
     * <p>{@code position}은 Riot 원시값이라 여기서 {@code ChampionPosition} 이름으로 정규화한다.
     * 알 수 없는 포지션과 NULL은 버린다 — 엔티티가 enum으로 읽으므로 저장되면 조회가 깨진다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO champion_item_stats (
                champion_id, position, item_id,
                purchase_count_all, purchase_count_recent,
                win_count_all, win_count_recent,
                champion_game_count_all, champion_game_count_recent
            )
            WITH participant AS (
                SELECT champion_id,
                       CASE position
                           WHEN 'MIDDLE' THEN 'MID'
                           WHEN 'UTILITY' THEN 'SUPPORT'
                           ELSE position
                       END AS normalized_position,
                       patch, win, core_item_purchase_order
                FROM normalized_match_participants
                WHERE (patch IS NULL OR patch NOT IN (:excludedPatches))
                  AND core_item_purchase_order_complete
                  AND core_item_purchase_order <> ''
                  AND position IN ('TOP', 'JUNGLE', 'MID', 'MIDDLE', 'BOTTOM', 'SUPPORT', 'UTILITY')
            ),
            champion_games AS (
                SELECT champion_id, normalized_position,
                       count(*) AS game_all,
                       count(*) FILTER (WHERE patch IN (:recentPatches)) AS game_recent
                FROM participant
                GROUP BY champion_id, normalized_position
            ),
            purchase AS (
                SELECT champion_id, normalized_position, patch, win,
                       unnest(string_to_array(core_item_purchase_order, ','))::bigint AS item_id
                FROM participant
            )
            SELECT purchase.champion_id, purchase.normalized_position, purchase.item_id,
                   count(*),
                   count(*) FILTER (WHERE purchase.patch IN (:recentPatches)),
                   count(*) FILTER (WHERE purchase.win),
                   count(*) FILTER (WHERE purchase.win AND purchase.patch IN (:recentPatches)),
                   max(champion_games.game_all),
                   max(champion_games.game_recent)
            FROM purchase
            JOIN champion_games
              ON champion_games.champion_id = purchase.champion_id
             AND champion_games.normalized_position = purchase.normalized_position
            GROUP BY purchase.champion_id, purchase.normalized_position, purchase.item_id
            """, nativeQuery = true)
    void aggregateFrom(@Param("recentPatches") Collection<String> recentPatches,
                       @Param("excludedPatches") Collection<String> excludedPatches);
}
