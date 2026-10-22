package dfgg.domain.rune;

import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NormalizedMatchParticipantRuneRepository
        extends JpaRepository<NormalizedMatchParticipantRune, Long> {

    /** 테이블 전체를 훑으므로 요청마다 부르지 않는다. 추천은 {@code RuneRecommendationPatch}를 거친다. */
    @Query("SELECT DISTINCT rune.patch FROM NormalizedMatchParticipantRune rune")
    List<String> findDistinctPatches();

    /** {@code (champion_id, position, patch)} 인덱스로 한 챔피언·포지션의 한 패치 기록을 읽는다. */
    List<NormalizedMatchParticipantRune> findByChampionIdAndPositionAndPatch(int championId, String position,
                                                                            String patch);

    /**
     * 참가자 룬을 한 문장으로 저장한다. 같은 경기·참가자가 이미 있으면 새 행을 만들지 않고 룬을 덮어쓴다.
     * JPA 저장은 IDENTITY 키 때문에 행마다 DB를 왕복하므로, 칸별 배열로 넘겨 unnest로 행을 펼친다.
     */
    default void upsertAll(List<NormalizedMatchParticipantRune> runes) {
        if (runes.isEmpty()) {
            return;
        }
        List<RunePage> pages = runes.stream().map(NormalizedMatchParticipantRune::runePage).toList();
        upsertColumns(
                column(runes, NormalizedMatchParticipantRune::matchId, String[]::new),
                column(runes, NormalizedMatchParticipantRune::participantId, Integer[]::new),
                column(runes, NormalizedMatchParticipantRune::patch, String[]::new),
                column(runes, NormalizedMatchParticipantRune::championId, Integer[]::new),
                column(runes, NormalizedMatchParticipantRune::teamId, Integer[]::new),
                column(runes, NormalizedMatchParticipantRune::position, String[]::new),
                column(runes, NormalizedMatchParticipantRune::win, Boolean[]::new),
                column(pages, RunePage::primaryStyleId, Integer[]::new),
                column(pages, RunePage::keystoneId, Integer[]::new),
                column(pages, page -> page.primaryPerkIds().get(1), Integer[]::new),
                column(pages, page -> page.primaryPerkIds().get(2), Integer[]::new),
                column(pages, page -> page.primaryPerkIds().get(3), Integer[]::new),
                column(pages, RunePage::subStyleId, Integer[]::new),
                column(pages, page -> page.subPerkIds().get(0), Integer[]::new),
                column(pages, page -> page.subPerkIds().get(1), Integer[]::new),
                column(pages, page -> page.statShards().offenseId(), Integer[]::new),
                column(pages, page -> page.statShards().flexId(), Integer[]::new),
                column(pages, page -> page.statShards().defenseId(), Integer[]::new)
        );
    }

    @Modifying
    @Query(value = """
            INSERT INTO normalized_match_participant_runes (
                match_id, participant_id, patch, champion_id, team_id, position, win,
                primary_style_id, keystone_id, primary_perk1_id, primary_perk2_id, primary_perk3_id,
                sub_style_id, sub_perk1_id, sub_perk2_id,
                stat_offense_id, stat_flex_id, stat_defense_id
            )
            SELECT *
            FROM unnest(
                CAST(:matchIds AS varchar[]), CAST(:participantIds AS integer[]), CAST(:patches AS varchar[]),
                CAST(:championIds AS integer[]), CAST(:teamIds AS integer[]), CAST(:positions AS varchar[]),
                CAST(:wins AS boolean[]),
                CAST(:primaryStyleIds AS integer[]), CAST(:keystoneIds AS integer[]),
                CAST(:primaryPerk1Ids AS integer[]), CAST(:primaryPerk2Ids AS integer[]),
                CAST(:primaryPerk3Ids AS integer[]),
                CAST(:subStyleIds AS integer[]), CAST(:subPerk1Ids AS integer[]), CAST(:subPerk2Ids AS integer[]),
                CAST(:statOffenseIds AS integer[]), CAST(:statFlexIds AS integer[]),
                CAST(:statDefenseIds AS integer[])
            )
            ON CONFLICT (match_id, participant_id) DO UPDATE SET
                patch = EXCLUDED.patch,
                champion_id = EXCLUDED.champion_id,
                team_id = EXCLUDED.team_id,
                position = EXCLUDED.position,
                win = EXCLUDED.win,
                primary_style_id = EXCLUDED.primary_style_id,
                keystone_id = EXCLUDED.keystone_id,
                primary_perk1_id = EXCLUDED.primary_perk1_id,
                primary_perk2_id = EXCLUDED.primary_perk2_id,
                primary_perk3_id = EXCLUDED.primary_perk3_id,
                sub_style_id = EXCLUDED.sub_style_id,
                sub_perk1_id = EXCLUDED.sub_perk1_id,
                sub_perk2_id = EXCLUDED.sub_perk2_id,
                stat_offense_id = EXCLUDED.stat_offense_id,
                stat_flex_id = EXCLUDED.stat_flex_id,
                stat_defense_id = EXCLUDED.stat_defense_id
            """, nativeQuery = true)
    void upsertColumns(
            @Param("matchIds") String[] matchIds,
            @Param("participantIds") Integer[] participantIds,
            @Param("patches") String[] patches,
            @Param("championIds") Integer[] championIds,
            @Param("teamIds") Integer[] teamIds,
            @Param("positions") String[] positions,
            @Param("wins") Boolean[] wins,
            @Param("primaryStyleIds") Integer[] primaryStyleIds,
            @Param("keystoneIds") Integer[] keystoneIds,
            @Param("primaryPerk1Ids") Integer[] primaryPerk1Ids,
            @Param("primaryPerk2Ids") Integer[] primaryPerk2Ids,
            @Param("primaryPerk3Ids") Integer[] primaryPerk3Ids,
            @Param("subStyleIds") Integer[] subStyleIds,
            @Param("subPerk1Ids") Integer[] subPerk1Ids,
            @Param("subPerk2Ids") Integer[] subPerk2Ids,
            @Param("statOffenseIds") Integer[] statOffenseIds,
            @Param("statFlexIds") Integer[] statFlexIds,
            @Param("statDefenseIds") Integer[] statDefenseIds
    );

    private static <T, V> V[] column(List<T> rows, Function<T, V> value, IntFunction<V[]> array) {
        return rows.stream().map(value).toArray(array);
    }
}
