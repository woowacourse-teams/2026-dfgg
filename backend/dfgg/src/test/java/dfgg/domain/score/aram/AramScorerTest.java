package dfgg.domain.score.aram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dfgg.domain.score.DfScore;
import dfgg.domain.score.GameDuration;
import dfgg.domain.score.ScoringParticipant;
import dfgg.domain.score.ScoringParticipants;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 기준값은 명세(docs/match-score.md 11절)대로 Python 참조 구현이 계산했다.
 */
class AramScorerTest {

    private final AramScorer scorer = new AramScorer();

    /** 100팀이 이긴 칼바람 경기. 포지션이 없다. 팀 킬은 45:31이고 p10은 20데스다. 시야 점수와 오브젝트 피해는 없다. */
    private ScoringParticipants referenceMatch() {
        return new ScoringParticipants(List.of(
                new ScoringParticipant(100, null, true, 12, 8, 20, 45000, 38000, 14000, null, 3000, null),
                new ScoringParticipant(100, null, true, 6, 10, 25, 28000, 52000, 12500, null, 1500, null),
                new ScoringParticipant(100, null, true, 9, 6, 18, 38000, 25000, 13500, null, 4000, null),
                new ScoringParticipant(100, null, true, 15, 7, 14, 52000, 30000, 15500, null, 2500, null),
                new ScoringParticipant(100, null, true, 3, 9, 30, 12000, 30000, 11000, null, 500, null),
                new ScoringParticipant(200, null, false, 8, 12, 10, 40000, 42000, 12800, null, 2000, null),
                new ScoringParticipant(200, null, false, 5, 14, 12, 25000, 60000, 11500, null, 800, null),
                new ScoringParticipant(200, null, false, 7, 11, 9, 36000, 28000, 12000, null, 1200, null),
                new ScoringParticipant(200, null, false, 10, 13, 6, 48000, 26000, 13200, null, 1800, null),
                new ScoringParticipant(200, null, false, 1, 20, 15, 9000, 35000, 9500, null, 0, null)));
    }

    @Test
    @DisplayName("칼바람(450)을 담당한다")
    void supports_WhenAramQueue_ReturnTrue() {
        // when
        boolean supported = scorer.supports(450);

        // then
        assertThat(supported).isTrue();
    }

    @Test
    @DisplayName("아수라장(2400)도 담당한다")
    void supports_WhenMayhemQueue_ReturnTrue() {
        // when
        boolean supported = scorer.supports(2400);

        // then
        assertThat(supported).isTrue();
    }

    @Test
    @DisplayName("협곡은 담당하지 않는다")
    void supports_WhenSummonersRiftQueue_ReturnFalse() {
        // when
        boolean supported = scorer.supports(420);

        // then
        assertThat(supported).isFalse();
    }

    @Test
    @DisplayName("8분(480초) 이상이면 채점한다")
    void isLongEnough_WhenEightMinutes_ReturnTrue() {
        // when
        boolean longEnough = scorer.isLongEnough(new GameDuration(480));

        // then
        assertThat(longEnough).isTrue();
    }

    @Test
    @DisplayName("8분 미만이면 채점하지 않는다")
    void isLongEnough_WhenShorterThanEightMinutes_ReturnFalse() {
        // when
        boolean longEnough = scorer.isLongEnough(new GameDuration(479));

        // then
        assertThat(longEnough).isFalse();
    }

    @Test
    @DisplayName("포지션이 없어도 통과한다(칼바람은 포지션이 없다)")
    void validate_WhenPositionsAreMissing_Pass() {
        // when & then
        assertThatCode(() -> scorer.validate(referenceMatch()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("데스 페널티와 표준화가 없다")
    void score_WhenReferenceMatch_EqualReferenceScores() {
        // when
        List<DfScore> scores = scorer.score(referenceMatch(), new GameDuration(1080));

        // then
        assertThat(scores).extracting(DfScore::value)
                .containsExactly(71.5, 56.4, 67.1, 69.4, 38.0, 51.3, 36.0, 38.2, 43.5, 11.2);
    }
}
