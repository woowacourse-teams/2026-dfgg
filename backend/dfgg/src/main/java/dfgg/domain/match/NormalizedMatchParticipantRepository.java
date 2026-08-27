package dfgg.domain.match;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NormalizedMatchParticipantRepository extends JpaRepository<NormalizedMatchParticipant, Long> {

    List<NormalizedMatchParticipant> findByMatchId(String matchId);

    boolean existsByMatchId(String matchId);

    @Query("""
            SELECT participant.puuid
            FROM NormalizedMatchParticipant participant
            WHERE participant.matchId = :matchId
              AND participant.tier = :tier
            """)
    List<String> findPuuidsByMatchIdAndTier(
            @Param("matchId") String matchId,
            @Param("tier") String tier
    );

    void deleteByMatchId(String matchId);

    List<NormalizedMatchParticipant> findByMatchIdIn(Collection<String> matchIds);

    @Query("""
            SELECT DISTINCT p.matchId
            FROM NormalizedMatchParticipant p
            ORDER BY p.matchId
            """)
    Slice<String> findDistinctMatchIds(Pageable pageable);

    @Query(value = """
            SELECT item_id, count(*)
            FROM (
                SELECT unnest(string_to_array(final_core_item_ids, ',')) AS item_id
                FROM normalized_match_participants
            ) AS item_tokens
            WHERE item_id <> ''
            GROUP BY item_id
            """, nativeQuery = true)
    List<Object[]> countItemOccurrences();

    /**
     * 이 챔피언·포지션이 실제로 코어 아이템으로 산 적 있는 아이템 ID를 중복 없이 반환한다.
     *
     * <p>탐색 구역(카운터 임베딩 공간)은 태그를 학습하지 않는다(콘텐츠 문맥은 정체성 공간
     * 전용). 그래서 "이 챔피언이 애초에 살 수 없는/안 사는 아이템"인지를 카운터 공간의
     * 코사인 유사도만으로는 판단할 수 없다 — 적과의 유사도가 아무리 높아도, 실측 구매
     * 이력에 없는 아이템은 이 결과로 걸러낸다. {@code position}에는 Riot 원시값이
     * 저장되므로 호출자가 {@code ChampionPositionNormalizer.riotValuesOf(...)}로 별칭까지
     * 넘겨야 한다.
     */
    @Query(value = """
            SELECT DISTINCT item_id
            FROM (
                SELECT unnest(string_to_array(final_core_item_ids, ',')) AS item_id
                FROM normalized_match_participants
                WHERE champion_id = :championId
                  AND position IN (:positions)
            ) AS purchased_items
            WHERE item_id <> ''
            """, nativeQuery = true)
    List<String> findDistinctPurchasedItemIds(
            @Param("championId") Long championId,
            @Param("positions") Collection<String> positions
    );

    /**
     * 챔피언·포지션에서 가장 많이 등장한 코어 아이템 빌드를 콤마 구분 문자열로 반환한다.
     *
     * <p>{@code position}에는 Riot 원시값이 저장되므로(정규화는 마이닝 시점에만 일어남)
     * 호출자가 {@code ChampionPositionNormalizer.riotValuesOf(...)}로 별칭까지 넘겨야 한다.
     */
    @Query(value = """
            SELECT final_core_item_ids
            FROM normalized_match_participants
            WHERE champion_id = :championId
              AND position IN (:positions)
              AND final_core_item_ids <> ''
            GROUP BY final_core_item_ids
            ORDER BY count(*) DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<String> findMostFrequentBuild(
            @Param("championId") Long championId,
            @Param("positions") Collection<String> positions
    );

    /**
     * {@code prefix}(콤마 구분 아이템 ID 문자열)로 구매 순서가 정확히 시작하는 참가자들만
     * 걸러, {@code nextPosition}번째(1-indexed) 아이템의 분포를 반환한다.
     *
     * <p>{@code mined_sequential_patterns}(PrefixSpan)와 달리 gap을 허용하지 않고 구매
     * 순서의 실제 앞부분에 정확히 anchoring된 통계다 — "빌드 어딘가에 있음"과 "그 위치에
     * 정확히 있음"을 혼동하지 않기 위함(1~2코어에서 이 차이가 크게 벌어짐을 실측으로 확인).
     * {@code prefix}가 빈 문자열이면 전체(필터 없음)를 대상으로 1번째 위치를 본다.
     *
     * <p>티어로 거르지 않는다. 요청자의 티어("누가 묻는가")와 학습 데이터의 티어("누구의
     * 판에서 배우는가")는 다른 개념인데, 예전엔 요청 티어를 그대로 조건에 넣어 수집 티어와
     * 다른 사용자는 0행을 받고 폴백 체인 맨 아래까지 떨어졌다.
     */
    @Query(value = """
            SELECT item_id, count(*) AS support, sum(win::int) AS win_count
            FROM (
                SELECT split_part(core_item_purchase_order, ',', :nextPosition) AS item_id, win
                FROM normalized_match_participants
                WHERE champion_id = :championId
                  AND position IN (:positions)
                  AND patch = :patch
                  AND (:prefix = '' OR core_item_purchase_order LIKE :prefix || ',%')
            ) AS next_items
            WHERE item_id <> ''
            GROUP BY item_id
            """, nativeQuery = true)
    List<Object[]> findNextItemDistribution(
            @Param("championId") Long championId,
            @Param("positions") Collection<String> positions,
            @Param("patch") String patch,
            @Param("prefix") String prefix,
            @Param("nextPosition") int nextPosition
    );
}
