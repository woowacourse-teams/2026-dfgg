package dfgg.application.counter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.counter.ChampionPositionStats;
import dfgg.domain.counter.ChampionPositionStatsRepository;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ChampionPositionAggregationService.class)
@Sql("/sql/champion-position-test-data.sql")
class ChampionPositionAggregationServiceTest {

    private static final int AHRI = 103;
    private static final int THRESH = 412;
    private static final int LEE_SIN = 64;

    @Autowired
    private ChampionPositionAggregationService aggregationService;

    @Autowired
    private ChampionPositionStatsRepository statsRepository;

    @Autowired
    private NormalizedMatchParticipantRepository participantRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("현재 패치(16.10)만 집계한다 — 16.9 경기는 세지 않는다")
    void aggregate_WhenOlderPatchExists_CountOnlyCurrentPatch() {
        // when
        ChampionPositionAggregationResult result = aggregationService.aggregate();

        // then
        assertThat(result.patch()).isEqualTo("16.10");
        assertThat(statsRepository.findAll())
                .extracting(ChampionPositionStats::getPatch)
                .containsOnly("16.10");
    }

    @Test
    @DisplayName("챔피언×포지션 등장 수를 저장하고, MIDDLE·UTILITY는 MID·SUPPORT로 맞춘다")
    void aggregate_WhenParticipantsGiven_SaveAppearanceCountPerChampionAndPosition() {
        // when
        aggregationService.aggregate();

        // then
        assertThat(statsRepository.findAll())
                .extracting(ChampionPositionStats::getChampionId, ChampionPositionStats::getPosition,
                        ChampionPositionStats::getGames)
                .containsExactlyInAnyOrder(
                        tuple(AHRI, ChampionPosition.MID, 2),
                        tuple(AHRI, ChampionPosition.TOP, 1),
                        tuple(THRESH, ChampionPosition.SUPPORT, 1),
                        tuple(LEE_SIN, ChampionPosition.JUNGLE, 1)
                );
    }

    @Test
    @DisplayName("포지션이 비었거나 없는 참가자는 집계하지 않는다")
    void aggregate_WhenPositionBlankOrNull_ExcludeParticipant() {
        // when
        ChampionPositionAggregationResult result = aggregationService.aggregate();

        // then
        assertThat(statsRepository.findAll())
                .extracting(ChampionPositionStats::getChampionId)
                .doesNotContain(22, 7);
        assertThat(result.savedRows()).isEqualTo(4);
    }

    @Test
    @DisplayName("같은 패치를 다시 집계해도 행이 중복되지 않는다")
    void aggregate_WhenRunTwiceForSamePatch_KeepSingleRowPerChampionAndPosition() {
        // given
        aggregationService.aggregate();

        // when
        ChampionPositionAggregationResult rerun = aggregationService.aggregate();

        // then
        assertThat(rerun.savedRows()).isEqualTo(4);
        assertThat(statsRepository.count()).isEqualTo(4);
    }

    @Test
    @DisplayName("같은 패치를 다시 집계하면 그 사이 늘어난 경기가 반영된다")
    void aggregate_WhenParticipantAddedBetweenRuns_ReflectLatestCount() {
        // given
        aggregationService.aggregate();
        jdbcTemplate.update("""
                INSERT INTO normalized_match_participants (
                    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, win,
                    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
                ) VALUES ('M5', '16.10', 420, 'm5-a', 1, 103, 100, 'MIDDLE', true, '', '', true)
                """);

        // when
        aggregationService.aggregate();

        // then
        assertThat(statsRepository.findAll())
                .filteredOn(stats -> stats.getChampionId() == AHRI && stats.getPosition() == ChampionPosition.MID)
                .extracting(ChampionPositionStats::getGames)
                .containsExactly(3);
    }

    @Test
    @DisplayName("현재 패치를 다시 집계해도 이전 패치 행은 남는다")
    void aggregate_WhenPreviousPatchRowsExist_KeepPreviousPatchRows() {
        // given
        jdbcTemplate.update(
                "INSERT INTO champion_position_stats (patch, champion_id, position, games) VALUES ('16.9', 103, 'MID', 7)");

        // when
        aggregationService.aggregate();

        // then
        assertThat(statsRepository.findAll())
                .filteredOn(stats -> stats.getPatch().equals("16.9"))
                .extracting(ChampionPositionStats::getChampionId, ChampionPositionStats::getGames)
                .containsExactly(tuple(AHRI, 7));
    }

    @Test
    @DisplayName("참가자가 없으면 아무것도 저장하지 않고 현재 패치도 비어 있다")
    void aggregate_WhenNoParticipant_SaveNothing() {
        // given
        participantRepository.deleteAllInBatch();

        // when
        ChampionPositionAggregationResult result = aggregationService.aggregate();

        // then
        assertThat(result.patch()).isNull();
        assertThat(result.savedRows()).isZero();
        assertThat(statsRepository.count()).isZero();
    }
}
