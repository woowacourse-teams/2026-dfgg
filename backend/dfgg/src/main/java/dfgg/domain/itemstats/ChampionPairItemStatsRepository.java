package dfgg.domain.itemstats;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChampionPairItemStatsRepository extends JpaRepository<ChampionPairItemStats, Long> {

    Optional<ChampionPairItemStats> findByMyChampionIdAndOtherChampionIdAndRelationAndItemId(
            Integer myChampionId, Integer otherChampionId, PairRelation relation, Long itemId
    );

    List<ChampionPairItemStats> findByMyChampionId(Integer myChampionId);

    List<ChampionPairItemStats> findByMyChampionIdAndOtherChampionId(
            Integer myChampionId, Integer otherChampionId
    );

    List<ChampionPairItemStats> findByMyChampionIdAndRelationAndOtherChampionIdIn(
            Integer myChampionId, PairRelation relation, Collection<Integer> otherChampionIds
    );

    /**
     * {@code [내 챔피언 + 상대 챔피언 + 아이템]} 삼중항을 원본 참가자 데이터에서 만든다.
     *
     * <p>핵심은 {@code purchaser}와 {@code context}의 비대칭이다. 아이템을 세는 쪽은 구매 순서가
     * 완전한 참가자로 제한하지만, 맥락이 되는 상대는 걸러내지 않는다 — 분모는 "그 조합이 함께
     * 등장한 판 수"이지 "상대의 구매 기록이 온전한 판 수"가 아니다. 상대를 같이 걸러내면
     * 분모만 줄어 확률이 부풀려진다.
     *
     * <p>자기 자신은 {@code puuid}로 제외한다. 챔피언 ID로 제외하면 미러 매치업
     * (양 팀에 같은 챔피언)이 통째로 사라지는데, 그건 실제로 존재하는 매치업이다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO champion_pair_item_stats (
                my_champion_id, other_champion_id, relation, item_id,
                co_count_all, co_count_recent,
                win_count_all, win_count_recent,
                pair_game_count_all, pair_game_count_recent
            )
            WITH purchaser AS (
                SELECT match_id, puuid, team_id, champion_id, patch, win, core_item_purchase_order
                FROM normalized_match_participants
                WHERE (patch IS NULL OR patch NOT IN (:excludedPatches))
                  AND core_item_purchase_order_complete
                  AND core_item_purchase_order <> ''
            ),
            context AS (
                SELECT match_id, puuid, team_id, champion_id
                FROM normalized_match_participants
            ),
            pair AS (
                SELECT purchaser.champion_id AS my_champion_id,
                       context.champion_id AS other_champion_id,
                       CASE WHEN purchaser.team_id = context.team_id THEN 'ALLY' ELSE 'ENEMY' END AS relation,
                       purchaser.patch, purchaser.win, purchaser.core_item_purchase_order
                FROM purchaser
                JOIN context
                  ON context.match_id = purchaser.match_id
                 AND context.puuid <> purchaser.puuid
            ),
            pair_games AS (
                SELECT my_champion_id, other_champion_id, relation,
                       count(*) AS game_all,
                       count(*) FILTER (WHERE patch IN (:recentPatches)) AS game_recent
                FROM pair
                GROUP BY my_champion_id, other_champion_id, relation
            ),
            pair_items AS (
                SELECT my_champion_id, other_champion_id, relation, patch, win,
                       unnest(string_to_array(core_item_purchase_order, ','))::bigint AS item_id
                FROM pair
            )
            SELECT pair_items.my_champion_id, pair_items.other_champion_id, pair_items.relation,
                   pair_items.item_id,
                   count(*),
                   count(*) FILTER (WHERE pair_items.patch IN (:recentPatches)),
                   count(*) FILTER (WHERE pair_items.win),
                   count(*) FILTER (WHERE pair_items.win AND pair_items.patch IN (:recentPatches)),
                   max(pair_games.game_all),
                   max(pair_games.game_recent)
            FROM pair_items
            JOIN pair_games
              ON pair_games.my_champion_id = pair_items.my_champion_id
             AND pair_games.other_champion_id = pair_items.other_champion_id
             AND pair_games.relation = pair_items.relation
            GROUP BY pair_items.my_champion_id, pair_items.other_champion_id, pair_items.relation,
                     pair_items.item_id
            """, nativeQuery = true)
    void aggregateFrom(@Param("recentPatches") Collection<String> recentPatches,
                       @Param("excludedPatches") Collection<String> excludedPatches);
}
