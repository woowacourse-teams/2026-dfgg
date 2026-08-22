package dfgg.application.sequence;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.sequence.SequentialPattern;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PrefixSpanMinerTest {

    private final PrefixSpanMiner miner = new PrefixSpanMiner();

    @Test
    @DisplayName("최소 지지도 이상인 부분수열 패턴만 정확한 지지도와 함께 마이닝한다")
    void mine_WhenSequencesGiven_ReturnsPatternsMeetingMinSupportWithCorrectSupportCount() {
        // given
        List<List<Long>> sequences = List.of(
                List.of(1L, 2L, 3L),
                List.of(1L, 3L),
                List.of(4L, 5L)
        );

        // when
        List<SequentialPattern> patterns = miner.mine(sequences, 2);

        // then
        assertThat(patterns).containsExactlyInAnyOrder(
                new SequentialPattern(List.of(1L), 2),
                new SequentialPattern(List.of(3L), 2),
                new SequentialPattern(List.of(1L, 3L), 2)
        );
    }

    @Test
    @DisplayName("시퀀스 사이에 다른 아이템이 끼어 있어도(gap 허용) 부분수열로 매칭해 지지도를 센다")
    void mine_WhenOtherItemsAppearBetween_StillCountsAsMatchingSubsequence() {
        // given
        List<List<Long>> sequences = List.of(
                List.of(1L, 2L, 3L, 4L),
                List.of(1L, 4L)
        );

        // when
        List<SequentialPattern> patterns = miner.mine(sequences, 2);

        // then
        assertThat(patterns).contains(new SequentialPattern(List.of(1L, 4L), 2));
    }

    @Test
    @DisplayName("최소 지지도 미만인 아이템은 패턴에서 제외한다")
    void mine_WhenItemBelowMinSupport_ExcludesItemFromPatterns() {
        // given
        List<List<Long>> sequences = List.of(
                List.of(1L, 2L, 3L),
                List.of(1L, 3L),
                List.of(4L, 5L)
        );

        // when
        List<SequentialPattern> patterns = miner.mine(sequences, 2);

        // then
        assertThat(patterns).extracting(SequentialPattern::items)
                .doesNotContain(List.of(2L), List.of(4L), List.of(5L));
    }
}
