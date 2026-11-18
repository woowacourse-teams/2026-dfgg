package dfgg.application.recommend.v3.ranker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 게임 상황까지 읽는 새 모델은 서버에 없을 수 있다. 없을 때 무엇으로 순위를 매길지를 이 객체가 답한다.
 */
class GameSituationRankingTest {

    private final CoreRanking baseline = new CoreRanking(mock(CandidateRanker.class), mock(TreeShapCalculator.class));
    private final CoreRanking gameSituation =
            new CoreRanking(mock(CandidateRanker.class), mock(TreeShapCalculator.class));

    @Test
    @DisplayName("새 모델이 없으면 없다고 답하고, 넘겨받은 지금 모델의 짝을 돌려준다")
    void orElse_WhenAbsent_ReturnsFallback() {
        // given
        GameSituationRanking ranking = GameSituationRanking.absent();

        // when
        CoreRanking chosen = ranking.orElse(baseline);

        // then
        assertThat(ranking.isAvailable()).isFalse();
        assertThat(chosen).isSameAs(baseline);
    }

    @Test
    @DisplayName("새 모델이 있으면 있다고 답하고, 지금 모델 대신 새 모델의 짝을 돌려준다")
    void orElse_WhenAvailable_ReturnsGameSituationRanking() {
        // given
        GameSituationRanking ranking = GameSituationRanking.available(gameSituation);

        // when
        CoreRanking chosen = ranking.orElse(baseline);

        // then
        assertThat(ranking.isAvailable()).isTrue();
        assertThat(chosen).isSameAs(gameSituation);
    }

    @Test
    @DisplayName("있다고 만들면서 짝을 주지 않으면 거부한다 — 없음은 absent()로만 만든다")
    void available_WhenRankingIsNull_Throws() {
        // when & then
        assertThatThrownBy(() -> GameSituationRanking.available(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("랭커와 기여도 계산기는 둘 다 있어야 짝이 된다")
    void coreRanking_WhenEitherIsNull_Throws() {
        // when & then
        assertThatThrownBy(() -> new CoreRanking(null, mock(TreeShapCalculator.class)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CoreRanking(mock(CandidateRanker.class), null))
                .isInstanceOf(NullPointerException.class);
    }
}
