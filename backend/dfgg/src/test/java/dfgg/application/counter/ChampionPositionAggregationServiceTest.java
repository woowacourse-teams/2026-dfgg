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
