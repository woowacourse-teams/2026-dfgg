package dfgg.application.rune;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dfgg.domain.match.RawMatchRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class ParticipantRuneNormalizationBatchServiceTest {

    private final RawMatchRepository rawMatchRepository = mock(RawMatchRepository.class);
    private final ParticipantRuneNormalizationService normalizationService =
            mock(ParticipantRuneNormalizationService.class);
    private final ParticipantRuneNormalizationBatchService batchService =
            new ParticipantRuneNormalizationBatchService(rawMatchRepository, normalizationService);

    @Test
    @DisplayName("패치의 경기를 커서 다음부터 정규화하고 실패한 경기는 기록한 뒤 넘어간다")
    void normalizePatch_RecordsFailureAndContinues() {
        // given
        when(rawMatchRepository.findMatchIdsForPatchAfter(eq("KR_0"), eq("16.19"), any(Pageable.class)))
                .thenReturn(List.of("KR_1", "KR_2", "KR_3"));
        when(normalizationService.normalize("KR_1")).thenReturn(10);
        when(normalizationService.normalize("KR_2")).thenThrow(new IllegalArgumentException("룬 계열이 없습니다."));
        when(normalizationService.normalize("KR_3")).thenReturn(10);

        // when
        RuneNormalizationBatchResult result = batchService.normalizePatch("16.19", "KR_0", 3);

        // then
        assertThat(result).isEqualTo(new RuneNormalizationBatchResult(3, 2, 1, 20, "KR_3", true,
                List.of("KR_2: 룬 계열이 없습니다.")));
        verify(rawMatchRepository).findMatchIdsForPatchAfter("KR_0", "16.19", PageRequest.of(0, 3));
    }

    @Test
    @DisplayName("배치가 limit보다 적게 차면 남은 경기가 없다고 알린다")
    void normalizePatch_WhenFewerThanLimit_HasNoMore() {
        // given
        when(rawMatchRepository.findMatchIdsForPatchAfter(eq(""), eq("16.19"), any(Pageable.class)))
                .thenReturn(List.of("KR_1"));
        when(normalizationService.normalize("KR_1")).thenReturn(10);

        // when
        RuneNormalizationBatchResult result = batchService.normalizePatch("16.19", "", 100);

        // then
        assertThat(result.hasMore()).isFalse();
        assertThat(result.nextCursor()).isEqualTo("KR_1");
    }

    @Test
    @DisplayName("남은 경기가 없으면 커서를 그대로 돌려준다")
    void normalizePatch_WhenNoMatches_KeepsCursor() {
        // given
        when(rawMatchRepository.findMatchIdsForPatchAfter(eq("KR_9"), eq("16.19"), any(Pageable.class)))
                .thenReturn(List.of());

        // when
        RuneNormalizationBatchResult result = batchService.normalizePatch("16.19", "KR_9", 100);

        // then
        assertThat(result).isEqualTo(new RuneNormalizationBatchResult(0, 0, 0, 0, "KR_9", false, List.of()));
    }
}
