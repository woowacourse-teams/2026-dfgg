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

    @Test
    @DisplayName("패턴이 시퀀스 안에 순서대로(gap 허용) 나타나면 매칭된 것으로 본다")
    void matches_WhenPatternIsGapAllowedSubsequenceOfSequence_ReturnsTrue() {
        // given
        List<Long> sequence = List.of(1L, 2L, 3L, 4L);
        List<Long> pattern = List.of(1L, 3L);

        // when
        boolean matched = miner.matches(sequence, pattern);

        // then
        assertThat(matched).isTrue();
    }

    @Test
    @DisplayName("패턴의 순서가 시퀀스와 다르면 매칭되지 않는다")
    void matches_WhenPatternOrderDiffersFromSequence_ReturnsFalse() {
        // given
        List<Long> sequence = List.of(1L, 2L, 3L, 4L);
        List<Long> pattern = List.of(3L, 1L);

        // when
        boolean matched = miner.matches(sequence, pattern);

        // then
        assertThat(matched).isFalse();
    }

    @Test
    @DisplayName("패턴의 아이템이 시퀀스에 없으면 매칭되지 않는다")
    void matches_WhenPatternItemIsMissingFromSequence_ReturnsFalse() {
        // given
        List<Long> sequence = List.of(1L, 2L, 3L);
        List<Long> pattern = List.of(1L, 9L);

        // when
        boolean matched = miner.matches(sequence, pattern);

        // then
        assertThat(matched).isFalse();
    }

    @Test
    @DisplayName("빈 패턴은 모든 시퀀스와 매칭된 것으로 본다")
    void matches_WhenPatternIsEmpty_ReturnsTrue() {
        // given
        List<Long> sequence = List.of(1L, 2L, 3L);
        List<Long> pattern = List.of();

        // when
        boolean matched = miner.matches(sequence, pattern);

        // then
        assertThat(matched).isTrue();
    }
}
