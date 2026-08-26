package dfgg.application.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CandidateZoneMixerTest {

    private final CandidateZoneMixer candidateZoneMixer = new CandidateZoneMixer();

    private List<RankedItemCandidate> rankedCandidates(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new RankedItemCandidate((long) i, 1.0 - i * 0.01))
                .toList();
    }

    @Test
    @DisplayName("요청한 비율(80/20)대로 총 개수를 정확히 나눈다")
    void mix_WhenRatioIsEightyTwenty_SplitsExactlyByRatio() {
        // given
        List<RankedItemCandidate> safeZone = rankedCandidates(20);
        List<RankedItemCandidate> explorationZone = rankedCandidates(20);

        // when
        MixedCandidates mixed = candidateZoneMixer.mix(safeZone, explorationZone, 10, 0.8);

        // then
        assertThat(mixed.safeZoneCandidates()).hasSize(8);
        assertThat(mixed.explorationZoneCandidates()).hasSize(2);
    }

    @Test
    @DisplayName("비율 파라미터를 다르게 주면 그 비율대로 나뉜다 (하드코딩된 80/20이 아님을 증명)")
    void mix_WhenRatioIsFiftyFifty_SplitsByGivenRatioNotHardcodedEightyTwenty() {
        // given
        List<RankedItemCandidate> safeZone = rankedCandidates(20);
        List<RankedItemCandidate> explorationZone = rankedCandidates(20);

        // when
        MixedCandidates mixed = candidateZoneMixer.mix(safeZone, explorationZone, 10, 0.5);

        // then
        assertThat(mixed.safeZoneCandidates()).hasSize(5);
        assertThat(mixed.explorationZoneCandidates()).hasSize(5);
    }

    @Test
    @DisplayName("각 구역에서 랭킹 상위 순서 그대로 잘라낸다")
    void mix_WhenSplitting_KeepsTopRankedOrderFromEachZone() {
        // given
        List<RankedItemCandidate> safeZone = rankedCandidates(20);
        List<RankedItemCandidate> explorationZone = rankedCandidates(20);

        // when
        MixedCandidates mixed = candidateZoneMixer.mix(safeZone, explorationZone, 10, 0.8);

        // then
        assertThat(mixed.safeZoneCandidates()).containsExactlyElementsOf(safeZone.subList(0, 8));
        assertThat(mixed.explorationZoneCandidates()).containsExactlyElementsOf(explorationZone.subList(0, 2));
    }

    @Test
    @DisplayName("안전 구역 후보가 요청한 개수보다 적으면 있는 만큼만 채택한다")
    void mix_WhenSafeZoneHasFewerCandidatesThanRequested_TakesOnlyAvailableOnes() {
        // given
        List<RankedItemCandidate> safeZone = rankedCandidates(3);
        List<RankedItemCandidate> explorationZone = rankedCandidates(20);

        // when
        MixedCandidates mixed = candidateZoneMixer.mix(safeZone, explorationZone, 10, 0.8);

        // then
        assertThat(mixed.safeZoneCandidates()).hasSize(3);
        assertThat(mixed.explorationZoneCandidates()).hasSize(2);
    }

    @Test
    @DisplayName("비율 계산이 딱 나눠떨어지지 않으면 반올림한 개수를 안전 구역에 배정한다")
    void mix_WhenRatioDoesNotDivideEvenly_RoundsSafeZoneCount() {
        // given: totalCandidateCount=3, ratio=0.8 → 3*0.8=2.4 → 반올림 2
        List<RankedItemCandidate> safeZone = rankedCandidates(20);
        List<RankedItemCandidate> explorationZone = rankedCandidates(20);

        // when
        MixedCandidates mixed = candidateZoneMixer.mix(safeZone, explorationZone, 3, 0.8);

        // then
        assertThat(mixed.safeZoneCandidates()).hasSize(2);
        assertThat(mixed.explorationZoneCandidates()).hasSize(1);
    }
}
