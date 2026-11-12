package dfgg.domain.score;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ScoringMatchTest {

    @Test
    @DisplayName("10명이 한 경기는 온전한 경기다")
    void isFullGame_WhenTenParticipants_ReturnTrue() {
        // given
        ScoringMatch match = new ScoringMatch(10);

        // when
        boolean fullGame = match.isFullGame();

        // then
        assertThat(fullGame).isTrue();
    }

    @ParameterizedTest(name = "참가자 {0}명")
    @ValueSource(ints = {0, 9, 11})
    @DisplayName("참가자가 10명이 아니면 채점하지 않는다")
    void isFullGame_WhenParticipantCountIsNotTen_ReturnFalse(int participantCount) {
        // given
        ScoringMatch match = new ScoringMatch(participantCount);

        // when
        boolean fullGame = match.isFullGame();

        // then
        assertThat(fullGame).isFalse();
    }
}
