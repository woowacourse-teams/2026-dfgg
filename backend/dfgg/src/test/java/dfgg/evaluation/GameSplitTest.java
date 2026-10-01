package dfgg.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GameSplitTest {

    private final GameSplit split = new GameSplit(0.8);

    @Test
    @DisplayName("같은 매치는 항상 같은 쪽으로 간다 — 한 게임의 여러 스냅샷이 train/test에 흩어지면 leakage다")
    void assign_WhenSameMatchId_AlwaysGoesToSameSide() {
        // given: 한 게임에서 여러 스냅샷(구매 단계별)이 나온다
        String matchId = "KR_7412345678";

        // when & then
        assertThat(split.isTrain(matchId)).isEqualTo(split.isTrain(matchId));
        assertThat(split.isTrain(matchId)).isEqualTo(split.isTrain(matchId));
    }

    @Test
    @DisplayName("실행할 때마다 같은 분할이 나온다 — 평가 결과를 재현할 수 있어야 한다")
    void assign_WhenNewInstance_ProducesIdenticalSplit() {
        // given
        List<String> matchIds = IntStream.range(0, 200).mapToObj(i -> "KR_" + i).toList();

        // when
        List<Boolean> first = matchIds.stream().map(split::isTrain).toList();
        List<Boolean> second = matchIds.stream().map(new GameSplit(0.8)::isTrain).toList();

        // then
        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("train과 test는 겹치지 않는다")
    void assign_WhenSplitting_TrainAndTestAreDisjoint() {
        // given
        List<String> matchIds = IntStream.range(0, 500).mapToObj(i -> "KR_" + i).toList();

        // when
        List<String> train = matchIds.stream().filter(split::isTrain).toList();
        List<String> test = matchIds.stream().filter(id -> !split.isTrain(id)).toList();

        // then
        assertThat(train).doesNotContainAnyElementsOf(test);
        assertThat(train.size() + test.size()).isEqualTo(matchIds.size());
    }

    @Test
    @DisplayName("지정한 비율에 근접하게 나뉜다")
    void assign_WhenManyMatches_ApproximatesRequestedRatio() {
        // given
        List<String> matchIds = IntStream.range(0, 10_000).mapToObj(i -> "KR_" + i).toList();

        // when
        long trainCount = matchIds.stream().filter(split::isTrain).count();

        // then: 해시 기반이라 정확히 8:2는 아니지만 근접해야 한다
        assertThat(trainCount).isBetween(7_600L, 8_400L);
    }

    @Test
    @DisplayName("매치 ID가 순차적이어도 한쪽에 몰리지 않는다 — 수집 순서가 분할에 새면 안 된다")
    void assign_WhenMatchIdsAreSequential_DoesNotClusterByOrder() {
        // given: 앞쪽 절반과 뒤쪽 절반
        List<String> earlier = IntStream.range(0, 2_000).mapToObj(i -> "KR_" + i).toList();
        List<String> later = IntStream.range(8_000, 10_000).mapToObj(i -> "KR_" + i).toList();

        // when
        double earlierTrainRatio = earlier.stream().filter(split::isTrain).count() / 2_000.0;
        double laterTrainRatio = later.stream().filter(split::isTrain).count() / 2_000.0;

        // then: 두 구간의 train 비율이 비슷해야 한다
        assertThat(Math.abs(earlierTrainRatio - laterTrainRatio)).isLessThan(0.06);
    }

    @Test
    @DisplayName("비율이 0과 1 사이가 아니면 거부한다")
    void construct_WhenRatioIsOutOfRange_ThrowsException() {
        assertThatThrownBy(() -> new GameSplit(0.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameSplit(1.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameSplit(-0.1)).isInstanceOf(IllegalArgumentException.class);
    }
}
