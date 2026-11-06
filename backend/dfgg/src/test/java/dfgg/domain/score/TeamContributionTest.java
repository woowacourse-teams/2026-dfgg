package dfgg.domain.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TeamContributionTest {

    @Test
    @DisplayName("팀 기여는 KP와 KDA 지수(KDA ÷ (KDA + 3))의 평균이다")
    void score_WhenKillParticipationAndKdaGiven_AverageBoth() {
        // given
        TeamContribution contribution = new TeamContribution(0.6, 9.0);

        // when
        double score = contribution.score();

        // then
        assertThat(score).isCloseTo(0.5 * 0.6 + 0.5 * (9.0 / (9.0 + 3)), within(1e-12));
    }

    @Test
    @DisplayName("KP 0.5, KDA 3이면 팀 기여는 0.5다")
    void score_WhenHalfParticipationAndKdaThree_ReturnHalf() {
        // given
        TeamContribution contribution = new TeamContribution(0.5, 3.0);

        // when
        double score = contribution.score();

        // then
        assertThat(score).isCloseTo(0.5, within(1e-12));
    }
}
