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

    private static final int PATCH_WINDOW = 3;

    private final NormalizedMatchParticipantRuneRepository repository =
            mock(NormalizedMatchParticipantRuneRepository.class);

    @Test
    @DisplayName("패치를 고정하지 않으면 룬 기록의 패치를 숫자로 비교해 높은 순으로 고른다")
    void candidates_WhenNotPinned_OrdersRecordedPatchesNumericallyDescending() {
        // given
        when(repository.findDistinctPatches()).thenReturn(List.of("16.9", "16.10", "16.2"));
        RuneRecommendationPatch recommendationPatch = new RuneRecommendationPatch(repository, "", PATCH_WINDOW);

        // when & then
        assertThat(recommendationPatch.candidates()).containsExactly("16.10", "16.9", "16.2");
    }

    @Test
    @DisplayName("후보는 최신 패치부터 설정한 개수까지만 둔다")
    void candidates_WhenMorePatchesThanWindow_KeepsLatestOnly() {
        // given
        when(repository.findDistinctPatches()).thenReturn(List.of("16.20", "16.23", "16.21", "16.22"));
        RuneRecommendationPatch recommendationPatch = new RuneRecommendationPatch(repository, "", 2);

        // when & then
        assertThat(recommendationPatch.candidates()).containsExactly("16.23", "16.22");
    }

    @Test
    @DisplayName("한 번 고른 후보는 기억해 두고 다시 조회하지 않는다")
    void candidates_WhenCalledTwice_QueriesOnce() {
        // given
        when(repository.findDistinctPatches()).thenReturn(List.of("16.20", "16.21"));
        RuneRecommendationPatch recommendationPatch = new RuneRecommendationPatch(repository, "", PATCH_WINDOW);

        // when
        recommendationPatch.candidates();
        recommendationPatch.candidates();

        // then
        verify(repository, times(1)).findDistinctPatches();
    }

    @Test
    @DisplayName("룬 기록이 아직 없으면 후보가 없고, 기록이 생긴 뒤 다시 조회한다")
    void candidates_WhenNoRecords_ReturnsEmptyAndRetriesLater() {
        // given
        when(repository.findDistinctPatches()).thenReturn(List.of(), List.of("16.21"));
        RuneRecommendationPatch recommendationPatch = new RuneRecommendationPatch(repository, "", PATCH_WINDOW);

        // when & then
        assertThat(recommendationPatch.candidates()).isEmpty();
        assertThat(recommendationPatch.candidates()).containsExactly("16.21");
    }

    @Test
    @DisplayName("패치를 고정하면 룬 기록을 조회하지 않고 그 패치만 후보로 둔다")
    void candidates_WhenPinned_UsesPinnedPatchOnlyWithoutQuery() {
        // given
        RuneRecommendationPatch recommendationPatch = new RuneRecommendationPatch(repository, " 16.19 ", PATCH_WINDOW);

        // when & then
        assertThat(recommendationPatch.candidates()).containsExactly("16.19");
        verifyNoInteractions(repository);
    }
}
