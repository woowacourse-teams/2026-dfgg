package dfgg.domain.score;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ScoringMatchTest {

    @Test
    @DisplayName("15분 이상, 10명이 한 경기는 채점한다")
    void isScorable_WhenFullGameAndLongEnough_ReturnTrue() {
        // given
        ScoringMatch match = new ScoringMatch(900, 10);

        // when
        boolean scorable = match.isScorable();

        // then
        assertThat(scorable).isTrue();
    }

    @Test
    @DisplayName("15분(900초) 미만인 경기는 채점하지 않는다")
    void isScorable_WhenShorterThanFifteenMinutes_ReturnFalse() {
        // given
        ScoringMatch match = new ScoringMatch(899, 10);

        // when
        boolean scorable = match.isScorable();

        // then
        assertThat(scorable).isFalse();
    }

    @ParameterizedTest(name = "참가자 {0}명")
    @ValueSource(ints = {0, 9, 11})
    @DisplayName("참가자가 10명이 아니면 채점하지 않는다")
    void isScorable_WhenParticipantCountIsNotTen_ReturnFalse(int participantCount) {
        // given
        ScoringMatch match = new ScoringMatch(1800, participantCount);

        // when
        boolean scorable = match.isScorable();

        // then
        assertThat(scorable).isFalse();
    }
}
