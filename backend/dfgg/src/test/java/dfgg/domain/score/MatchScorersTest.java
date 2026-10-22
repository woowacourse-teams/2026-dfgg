package dfgg.domain.score;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.score.summonersrift.SummonersRiftScorer;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MatchScorersTest {

    @Test
    @DisplayName("큐를 맡는 채점기를 찾는다")
    void find_WhenScorerSupportsQueue_ReturnIt() {
        // given
        SummonersRiftScorer summonersRiftScorer = new SummonersRiftScorer();
        MatchScorers scorers = new MatchScorers(List.of(summonersRiftScorer));

        // when
        Optional<MatchScorer> found = scorers.find(420);

        // then
        assertThat(found).containsSame(summonersRiftScorer);
    }

    @Test
    @DisplayName("큐를 맡는 채점기가 없으면 비어 있다 — 채점 대상이 아니다")
    void find_WhenNoScorerSupportsQueue_ReturnEmpty() {
        // given
        MatchScorers scorers = new MatchScorers(List.of(new SummonersRiftScorer()));

        // when
        Optional<MatchScorer> found = scorers.find(450);

        // then
        assertThat(found).isEmpty();
    }
}
