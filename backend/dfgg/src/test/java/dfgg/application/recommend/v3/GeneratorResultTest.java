package dfgg.application.recommend.v3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GeneratorResultTest {

    @Test
    @DisplayName("rank는 목록 순서대로 1부터 매겨진다 — generator가 rank를 직접 넘기지 않는다")
    void of_WhenItemsGiven_AssignsRankByListPositionStartingAtOne() {
        // given
        List<ScoredItem> items = List.of(
                new ScoredItem(3031L, 0.93),
                new ScoredItem(6673L, 0.81),
                new ScoredItem(3006L, 0.42)
        );

        // when
        GeneratorResult result = GeneratorResult.of(CandidateSource.BUILD, items);

        // then
        assertThat(result.rankOf(3031L)).isEqualTo(1);
        assertThat(result.rankOf(6673L)).isEqualTo(2);
        assertThat(result.rankOf(3006L)).isEqualTo(3);
    }

    @Test
    @DisplayName("점수가 내림차순이 아니면 거부한다 — 순서와 rank가 어긋나는 generator 버그를 조기에 잡는다")
    void of_WhenScoresAreNotInDescendingOrder_ThrowsException() {
        // given
        List<ScoredItem> unsorted = List.of(
                new ScoredItem(3031L, 0.42),
                new ScoredItem(6673L, 0.93)
        );

        // when & then
        assertThatThrownBy(() -> GeneratorResult.of(CandidateSource.BUILD, unsorted))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("내림차순");
    }

    @Test
    @DisplayName("점수가 같아 동점이면 허용한다")
    void of_WhenScoresAreTied_IsAccepted() {
        // given
        List<ScoredItem> tied = List.of(
                new ScoredItem(3031L, 0.5),
                new ScoredItem(6673L, 0.5)
        );

        // when
        GeneratorResult result = GeneratorResult.of(CandidateSource.BUILD, tied);

        // then
        assertThat(result.rankOf(3031L)).isEqualTo(1);
        assertThat(result.rankOf(6673L)).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 아이템이 한 generator 결과에 두 번 나오면 거부한다")
    void of_WhenItemIdIsDuplicated_ThrowsException() {
        // given
        List<ScoredItem> duplicated = List.of(
                new ScoredItem(3031L, 0.9),
                new ScoredItem(3031L, 0.5)
        );

        // when & then
        assertThatThrownBy(() -> GeneratorResult.of(CandidateSource.BUILD, duplicated))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("중복");
    }

    @Test
    @DisplayName("후보가 없는 generator 결과도 만들 수 있다")
    void of_WhenNoItems_CreatesEmptyResult() {
        // when
        GeneratorResult result = GeneratorResult.of(CandidateSource.COUNTER, List.of());

        // then
        assertThat(result.isEmpty()).isTrue();
        assertThat(result.source()).isEqualTo(CandidateSource.COUNTER);
    }
}
