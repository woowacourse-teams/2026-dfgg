package dfgg.application.rune;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dfgg.domain.rune.NormalizedMatchParticipantRuneRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RuneRecommendationPatchTest {

    private final NormalizedMatchParticipantRuneRepository repository =
            mock(NormalizedMatchParticipantRuneRepository.class);

    @Test
    @DisplayName("패치를 고정하지 않으면 룬 기록의 가장 높은 패치를 숫자로 비교해 고른다")
    void current_WhenNotPinned_PicksHighestRecordedPatchNumerically() {
        // given
        when(repository.findDistinctPatches()).thenReturn(List.of("16.9", "16.10", "16.2"));
        RuneRecommendationPatch recommendationPatch = new RuneRecommendationPatch(repository, "");

        // when & then
        assertThat(recommendationPatch.current()).contains("16.10");
    }

    @Test
    @DisplayName("한 번 고른 패치는 기억해 두고 다시 조회하지 않는다")
    void current_WhenCalledTwice_QueriesOnce() {
        // given
        when(repository.findDistinctPatches()).thenReturn(List.of("16.20", "16.21"));
        RuneRecommendationPatch recommendationPatch = new RuneRecommendationPatch(repository, "");

        // when
        recommendationPatch.current();
        recommendationPatch.current();

        // then
        verify(repository, times(1)).findDistinctPatches();
    }

    @Test
    @DisplayName("룬 기록이 아직 없으면 패치가 없고, 기록이 생긴 뒤 다시 조회한다")
    void current_WhenNoRecords_ReturnsEmptyAndRetriesLater() {
        // given
        when(repository.findDistinctPatches()).thenReturn(List.of(), List.of("16.21"));
        RuneRecommendationPatch recommendationPatch = new RuneRecommendationPatch(repository, "");

        // when & then
        assertThat(recommendationPatch.current()).isEmpty();
        assertThat(recommendationPatch.current()).contains("16.21");
    }

    @Test
    @DisplayName("패치를 고정하면 룬 기록을 조회하지 않고 그 패치를 쓴다")
    void current_WhenPinned_UsesPinnedPatchWithoutQuery() {
        // given
        RuneRecommendationPatch recommendationPatch = new RuneRecommendationPatch(repository, " 16.19 ");

        // when & then
        assertThat(recommendationPatch.current()).contains("16.19");
        verifyNoInteractions(repository);
    }
}
