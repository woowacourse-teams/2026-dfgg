package dfgg.domain.score;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GameDurationTest {

    @Test
    @DisplayName("게임 시간을 분으로 바꾼다")
    void minutes_WhenSecondsGiven_DivideBySixty() {
        // given
        GameDuration duration = new GameDuration(1815);

        // when
        double minutes = duration.minutes();

        // then
        assertThat(minutes).isEqualTo(30.25);
    }

    @Test
    @DisplayName("기준 시간 이상인지 안다")
    void isAtLeast_WhenComparedWithMinimumSeconds_TrueOnlyFromMinimum() {
        // when & then
        assertThat(new GameDuration(900).isAtLeast(900)).isTrue();
        assertThat(new GameDuration(899).isAtLeast(900)).isFalse();
    }
}
