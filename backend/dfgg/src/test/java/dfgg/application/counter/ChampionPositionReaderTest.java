package dfgg.application.counter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.counter.ChampionPositionStats;
import dfgg.domain.counter.ChampionPositionStatsRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChampionPositionReaderTest {

    private static final int LEE_SIN = 64;

    private final ChampionPositionStatsRepository repository = mock(ChampionPositionStatsRepository.class);
    private final ChampionPositionReader reader = new ChampionPositionReader(repository);

    @Test
    @DisplayName("가장 최근 패치의 라인 분포에서 가장 많이 간 라인을 주 라인으로 고른다")
    void mainPositionOf_WhenStatsExist_ReturnMostPlayedPositionOfLatestPatch() {
        // given
        List<ChampionPositionStats> stored = List.of(
                positionStats(ChampionPosition.TOP, 3),
                positionStats(ChampionPosition.JUNGLE, 90));
        given(repository.findDistinctPatches()).willReturn(List.of("16.9", "16.10"));
        given(repository.findByPatchAndChampionIdIn("16.10", List.of(LEE_SIN))).willReturn(stored);

        // when
        Optional<ChampionPosition> mainPosition = reader.mainPositionOf(LEE_SIN);

        // then
        assertThat(mainPosition).contains(ChampionPosition.JUNGLE);
    }

    @Test
    @DisplayName("그 챔피언의 라인 기록이 없으면 주 라인이 없다")
    void mainPositionOf_WhenChampionHasNoStats_ReturnEmpty() {
        // given
        given(repository.findDistinctPatches()).willReturn(List.of("16.10"));
        given(repository.findByPatchAndChampionIdIn("16.10", List.of(LEE_SIN))).willReturn(List.of());

        // when
        Optional<ChampionPosition> mainPosition = reader.mainPositionOf(LEE_SIN);

        // then
        assertThat(mainPosition).isEmpty();
    }

    @Test
    @DisplayName("라인 분포 집계가 하나도 없으면 주 라인이 없다")
    void mainPositionOf_WhenNoPatchAggregated_ReturnEmpty() {
        // given
        given(repository.findDistinctPatches()).willReturn(List.of());

        // when
        Optional<ChampionPosition> mainPosition = reader.mainPositionOf(LEE_SIN);

        // then
        assertThat(mainPosition).isEmpty();
    }

    private static ChampionPositionStats positionStats(ChampionPosition position, int games) {
        ChampionPositionStats stats = mock(ChampionPositionStats.class);
        given(stats.getChampionId()).willReturn(LEE_SIN);
        given(stats.getPosition()).willReturn(position);
        given(stats.getGames()).willReturn(games);
        return stats;
    }
}
